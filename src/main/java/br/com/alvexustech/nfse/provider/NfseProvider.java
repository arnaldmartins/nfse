package br.com.alvexustech.nfse.provider;

import br.com.alvexustech.nfse.dto.EmitirNfseRequest;
import br.com.alvexustech.nfse.dto.NationalEmissionResponse;
import reactor.core.publisher.Mono;

public interface NfseProvider {

    String code();

    Mono<NationalEmissionResponse> emit(EmitirNfseRequest request, String signedXml);

    Mono<NationalEmissionResponse> consultStatus(String protocolo);

    Mono<NationalEmissionResponse> consultDps(String idDps);
}
