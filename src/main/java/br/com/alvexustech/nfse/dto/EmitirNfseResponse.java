package br.com.alvexustech.nfse.dto;

import br.com.alvexustech.nfse.domain.EmissionStatus;

import java.util.UUID;

public record EmitirNfseResponse(
        UUID emissionId,
        EmissionStatus status,
        String protocolo,
        String chaveAcesso
) {
}
