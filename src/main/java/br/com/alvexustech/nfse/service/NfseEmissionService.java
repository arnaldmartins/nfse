package br.com.alvexustech.nfse.service;

import br.com.alvexustech.nfse.config.NfseEmissionProperties;
import br.com.alvexustech.nfse.config.NfseMunicipioProperties;
import br.com.alvexustech.nfse.domain.EmissionStatus;
import br.com.alvexustech.nfse.dto.ConsultarStatusResponse;
import br.com.alvexustech.nfse.dto.EmitirNfseRequest;
import br.com.alvexustech.nfse.dto.EmitirNfseResponse;
import br.com.alvexustech.nfse.persistence.NfseEmissionEntity;
import br.com.alvexustech.nfse.provider.NfseProviderRegistry;
import br.com.alvexustech.nfse.repository.NfseEmissionRepository;
import br.com.alvexustech.nfse.signature.XmlSignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@Service
public class NfseEmissionService {

    private static final Logger log = LoggerFactory.getLogger(NfseEmissionService.class);

    private final NfseEmissionRepository repository;
    private final DpsXmlBuilder dpsXmlBuilder;
    private final XmlSignatureService xmlSignatureService;
    private final NfseXmlValidator nfseXmlValidator;
    private final NfseProviderRegistry providerRegistry;
    private final NfseEmissionProperties emissionProperties;
    private final NfseMunicipioProperties municipioProperties;
    private final TransactionTemplate transactionTemplate;

    public NfseEmissionService(NfseEmissionRepository repository, DpsXmlBuilder dpsXmlBuilder,
                               XmlSignatureService xmlSignatureService, NfseXmlValidator nfseXmlValidator,
                               NfseProviderRegistry providerRegistry,
                               NfseEmissionProperties emissionProperties, NfseMunicipioProperties municipioProperties,
                               TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.dpsXmlBuilder = dpsXmlBuilder;
        this.xmlSignatureService = xmlSignatureService;
        this.nfseXmlValidator = nfseXmlValidator;
        this.providerRegistry = providerRegistry;
        this.emissionProperties = emissionProperties;
        this.municipioProperties = municipioProperties;
        this.transactionTemplate = transactionTemplate;
    }

    public Mono<EmitirNfseResponse> emit(EmitirNfseRequest request) {
        return Mono.fromCallable(() -> transactionTemplate.execute(status -> prepareEmission(request)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(preparation -> {
                    if (!preparation.newEmission()) {
                        return Mono.just(toResponse(preparation.entity()));
                    }
                    NfseEmissionEntity entity = preparation.entity();
                    return providerRegistry.get(entity.getProvider()).emit(request, entity.getSignedXml())
                            .flatMap(response -> Mono.fromCallable(() -> transactionTemplate.execute(status -> updateFromProvider(entity.getId(), response)))
                                    .subscribeOn(Schedulers.boundedElastic()));
                })
                .onErrorResume(ex -> Mono.fromCallable(() -> transactionTemplate.execute(status -> markFailed(request.idempotencyKey(), ex)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    public Mono<ConsultarStatusResponse> consultStatus(UUID emissionId) {
        return Mono.fromCallable(() -> repository.findById(emissionId)
                        .orElseThrow(() -> new IllegalArgumentException("Emissao nao encontrada: " + emissionId)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(entity -> {
                    if (entity.getProviderProtocol() == null || entity.getProviderProtocol().isBlank()) {
                        return Mono.just(new ConsultarStatusResponse(entity.getStatus(), entity.getProviderProtocol(),
                                entity.getAccessKey(), "Emissao ainda sem protocolo do provider"));
                    }
                    return providerRegistry.get(entity.getProvider())
                        .consultStatus(entity.getProviderProtocol())
                        .flatMap(response -> Mono.fromCallable(() -> {
                            return transactionTemplate.execute(status -> {
                                entity.setResponsePayload(response.rawPayload());
                                entity.setStatus(mapStatus(response.status()));
                                repository.save(entity);
                                return new ConsultarStatusResponse(entity.getStatus(), entity.getProviderProtocol(),
                                        entity.getAccessKey(), response.mensagem());
                            });
                        }).subscribeOn(Schedulers.boundedElastic()));
                });
    }

    private EmissionPreparation prepareEmission(EmitirNfseRequest request) {
        var existing = repository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            log.info("nfse_emission_idempotent_hit id={} key={}", existing.get().getId(), request.idempotencyKey());
            return new EmissionPreparation(existing.get(), false);
        }

        NfseEmissionEntity entity = new NfseEmissionEntity(
                UUID.randomUUID(),
                request.idempotencyKey(),
                emissionProperties.defaultProvider(),
                municipioProperties.codigoIbge(),
                EmissionStatus.RECEIVED,
                request.prestador().cnpj(),
                request.tomador().cpf(),
                request.servico().valor()
        );

        DpsXmlBuilder.DpsXml dpsXml = dpsXmlBuilder.build(request);
        nfseXmlValidator.validateDps(dpsXml.xml());
        entity.setDpsXml(dpsXml.xml());
        String signedXml = xmlSignatureService.sign(dpsXml.xml(), dpsXml.id());
        nfseXmlValidator.validateDps(signedXml);
        entity.setSignedXml(signedXml);
        entity.setStatus(EmissionStatus.SIGNED);

        NfseEmissionEntity saved = repository.save(entity);
        log.info("nfse_emission_prepared id={} provider={} municipio={}", saved.getId(), saved.getProvider(), saved.getMunicipalityCode());
        return new EmissionPreparation(saved, true);
    }

    private EmitirNfseResponse updateFromProvider(UUID id, br.com.alvexustech.nfse.dto.NationalEmissionResponse response) {
        NfseEmissionEntity entity = repository.findById(id).orElseThrow();
        entity.setProviderProtocol(response.protocolo());
        entity.setResponsePayload(response.rawPayload());
        entity.setStatus(mapStatus(response.status()));
        if (entity.getStatus() == EmissionStatus.AUTHORIZED) {
            entity.markAuthorized(response.chaveAcesso());
        }
        repository.save(entity);
        return toResponse(entity);
    }

    private EmitirNfseResponse markFailed(String idempotencyKey, Throwable ex) {
        repository.findByIdempotencyKey(idempotencyKey).ifPresent(entity -> {
            entity.setStatus(EmissionStatus.FAILED);
            entity.setError("PROVIDER_ERROR", ex.getMessage());
            repository.save(entity);
        });
        throw new IllegalStateException("Falha na emissao de NFS-e", ex);
    }

    private EmissionStatus mapStatus(String providerStatus) {
        if (providerStatus == null) {
            return EmissionStatus.PROCESSING;
        }
        return switch (providerStatus.toUpperCase()) {
            case "AUTORIZADA", "AUTHORIZED" -> EmissionStatus.AUTHORIZED;
            case "REJEITADA", "REJECTED" -> EmissionStatus.REJECTED;
            case "CANCELADA", "CANCELLED" -> EmissionStatus.CANCELLED;
            case "PROCESSANDO", "PROCESSING" -> EmissionStatus.PROCESSING;
            default -> EmissionStatus.SENT;
        };
    }

    private EmitirNfseResponse toResponse(NfseEmissionEntity entity) {
        return new EmitirNfseResponse(entity.getId(), entity.getStatus(), entity.getProviderProtocol(), entity.getAccessKey());
    }

    private record EmissionPreparation(NfseEmissionEntity entity, boolean newEmission) {
    }
}
