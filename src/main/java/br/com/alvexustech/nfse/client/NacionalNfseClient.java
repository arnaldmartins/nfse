package br.com.alvexustech.nfse.client;

import br.com.alvexustech.nfse.config.NationalApiProperties;
import br.com.alvexustech.nfse.dto.NationalEmissionPayload;
import br.com.alvexustech.nfse.dto.NationalEmissionResponse;
import br.com.alvexustech.nfse.exception.NfseProviderException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class NacionalNfseClient {

    private final WebClient webClient;
    private final NationalApiProperties properties;

    public NacionalNfseClient(WebClient nationalNfseWebClient, NationalApiProperties properties) {
        this.webClient = nationalNfseWebClient;
        this.properties = properties;
    }

    public Mono<NationalEmissionResponse> emit(NationalEmissionPayload payload, String idempotencyKey) {
        return webClient.post()
                .uri(properties.emissionPath())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new NfseProviderException("Erro na emissao NFS-e Nacional: " + body)))
                .bodyToMono(NationalEmissionResponse.class);
    }

    public Mono<NationalEmissionResponse> status(String protocolo) {
        return webClient.get()
                .uri(properties.statusPathTemplate(), Map.of("protocolo", protocolo))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new NfseProviderException("Erro na consulta NFS-e Nacional: " + body)))
                .bodyToMono(NationalEmissionResponse.class);
    }
}
