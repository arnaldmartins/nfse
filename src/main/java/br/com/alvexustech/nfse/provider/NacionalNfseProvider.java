package br.com.alvexustech.nfse.provider;

import br.com.alvexustech.nfse.client.NacionalNfseClient;
import br.com.alvexustech.nfse.dto.EmitirNfseRequest;
import br.com.alvexustech.nfse.dto.NationalEmissionPayload;
import br.com.alvexustech.nfse.dto.NationalEmissionResponse;
import br.com.alvexustech.nfse.util.CompressionUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class NacionalNfseProvider implements NfseProvider {

    private final NacionalNfseClient client;

    public NacionalNfseProvider(NacionalNfseClient client) {
        this.client = client;
    }

    @Override
    public String code() {
        return "nacional";
    }

    @Override
    public Mono<NationalEmissionResponse> emit(EmitirNfseRequest request, String signedXml) {
        NationalEmissionPayload payload = new NationalEmissionPayload(CompressionUtils.gzipBase64(signedXml));
        return client.emit(payload, request.idempotencyKey());
    }

    @Override
    public Mono<NationalEmissionResponse> consultStatus(String chaveAcesso) {
        return client.status(chaveAcesso);
    }
    
    @Override
    public Mono<NationalEmissionResponse> consultDps(String idDps) {
        return client.dps(idDps);
    }

}
