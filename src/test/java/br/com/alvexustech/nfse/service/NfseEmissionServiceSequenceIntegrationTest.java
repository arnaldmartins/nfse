package br.com.alvexustech.nfse.service;

import br.com.alvexustech.nfse.PostgresIntegrationTest;
import br.com.alvexustech.nfse.config.NfseEmissionProperties;
import br.com.alvexustech.nfse.config.NfseMunicipioProperties;
import br.com.alvexustech.nfse.domain.EmissionStatus;
import br.com.alvexustech.nfse.dto.EmitirNfseRequest;
import br.com.alvexustech.nfse.dto.NationalEmissionResponse;
import br.com.alvexustech.nfse.dto.PrestadorDto;
import br.com.alvexustech.nfse.dto.ServicoDto;
import br.com.alvexustech.nfse.dto.TomadorDto;
import br.com.alvexustech.nfse.persistence.NfseDpsSequenceEntity;
import br.com.alvexustech.nfse.persistence.NfseEmissionEntity;
import br.com.alvexustech.nfse.provider.NfseProvider;
import br.com.alvexustech.nfse.provider.NfseProviderRegistry;
import br.com.alvexustech.nfse.repository.NfseDpsSequenceRepository;
import br.com.alvexustech.nfse.repository.NfseEmissionRepository;
import br.com.alvexustech.nfse.signature.XmlSignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({NfseEmissionService.class, NfseDpsSequenceService.class})
@EnableConfigurationProperties({NfseEmissionProperties.class, NfseMunicipioProperties.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "nfse.emission.default-provider=nacional",
        "nfse.emission.ambiente-codigo=2",
        "nfse.emission.serie-dps=7",
        "nfse.municipio.codigo-ibge=3106200",
        "nfse.municipio.nome=Belo Horizonte",
        "nfse.municipio.uf=MG"
})
class NfseEmissionServiceSequenceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private NfseEmissionService service;
    @Autowired
    private NfseEmissionRepository emissionRepository;
    @Autowired
    private NfseDpsSequenceRepository sequenceRepository;
    @MockBean
    private DpsXmlBuilder dpsXmlBuilder;
    @MockBean
    private XmlSignatureService xmlSignatureService;
    @MockBean
    private NfseXmlValidator nfseXmlValidator;
    @MockBean
    private NfseProviderRegistry providerRegistry;
    @MockBean
    private NfseProvider provider;

    @BeforeEach
    void cleanDatabase() {
        emissionRepository.deleteAll();
        sequenceRepository.deleteAll();
    }

    @Test
    void allocatesOneDpsNumberInsidePreparationTransactionForNewEmission() {
        sequenceRepository.saveAndFlush(new NfseDpsSequenceEntity(UUID.randomUUID(), "66375620000113", 7, 41L));
        EmitirNfseRequest request = request("pedido-novo-sequencial");
        when(dpsXmlBuilder.build(request, 7, 42L)).thenReturn(new DpsXmlBuilder.DpsXml("DPS42", "<DPS/>"));
        when(xmlSignatureService.sign("<DPS/>", "DPS42")).thenReturn("<DPS>signed</DPS>");
        when(providerRegistry.get("nacional")).thenReturn(provider);
        when(provider.emit(request, "<DPS>signed</DPS>")).thenReturn(Mono.just(authorizedResponse()));

        service.emit(request).block();

        NfseEmissionEntity emission = emissionRepository.findByIdempotencyKey("pedido-novo-sequencial").orElseThrow();
        NfseDpsSequenceEntity sequence = sequenceRepository.findByIssuerCnpjAndDpsSerial("66375620000113", 7)
                .orElseThrow();
        assertThat(emission.getIssuerCnpj()).isEqualTo("66375620000113");
        assertThat(emission.getDpsSerial()).isEqualTo(7);
        assertThat(emission.getDpsNumber()).isEqualTo(42L);
        assertThat(emission.getSignedXml()).isEqualTo("<DPS>signed</DPS>");
        assertThat(sequence.getLastDpsIssued()).isEqualTo(42L);
        verify(dpsXmlBuilder).build(request, 7, 42L);
    }

    @Test
    void returnsExistingEmissionWithoutAllocatingSequenceOrRebuildingXml() {
        NfseEmissionEntity existing = emission("pedido-idempotente");
        existing.setDpsNumber(7, 42L);
        emissionRepository.saveAndFlush(existing);
        sequenceRepository.saveAndFlush(new NfseDpsSequenceEntity(UUID.randomUUID(), "66375620000113", 7, 42L));

        service.emit(request("pedido-idempotente")).block();

        NfseDpsSequenceEntity sequence = sequenceRepository.findByIssuerCnpjAndDpsSerial("66375620000113", 7)
                .orElseThrow();
        assertThat(sequence.getLastDpsIssued()).isEqualTo(42L);
        verifyNoInteractions(dpsXmlBuilder, xmlSignatureService, nfseXmlValidator, providerRegistry, provider);
    }

    @Test
    void rollsBackSequenceIncrementWhenLocalPreparationFails() {
        sequenceRepository.saveAndFlush(new NfseDpsSequenceEntity(UUID.randomUUID(), "66375620000113", 7, 41L));
        EmitirNfseRequest request = request("pedido-falha-local");
        when(dpsXmlBuilder.build(request, 7, 42L)).thenThrow(new IllegalStateException("xml invalido"));

        assertThatThrownBy(() -> service.emit(request).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Falha na emissao de NFS-e");

        NfseDpsSequenceEntity sequence = sequenceRepository.findByIssuerCnpjAndDpsSerial("66375620000113", 7)
                .orElseThrow();
        assertThat(sequence.getLastDpsIssued()).isEqualTo(41L);
        assertThat(emissionRepository.findByIdempotencyKey("pedido-falha-local")).isEmpty();
        verify(providerRegistry, never()).get(any());
    }

    private EmitirNfseRequest request(String idempotencyKey) {
        return new EmitirNfseRequest(
                idempotencyKey,
                LocalDate.of(2025, 6, 16),
                new PrestadorDto("66.375.620/0001-13", ""),
                new TomadorDto("11905650647", "Arnald Alves Martins", "arnaldalvesmartins@gmail.com"),
                new ServicoDto("01.01", "Desenvolvimento de software", new BigDecimal("1000.00"), "0101")
        );
    }

    private NfseEmissionEntity emission(String idempotencyKey) {
        NfseEmissionEntity emission = new NfseEmissionEntity(
                UUID.randomUUID(),
                idempotencyKey,
                "nacional",
                "3106200",
                EmissionStatus.SIGNED,
                "66375620000113",
                "11905650647",
                new BigDecimal("1000.00")
        );
        emission.setDpsXml("<DPS/>");
        emission.setSignedXml("<DPS>signed</DPS>");
        return emission;
    }

    private NationalEmissionResponse authorizedResponse() {
        return new NationalEmissionResponse(2, "app", "2025-06-16T10:00:00-03:00",
                "DPS42", "CHAVE", null, null, null, "PROTOCOLO", "100", "Autorizada");
    }
}
