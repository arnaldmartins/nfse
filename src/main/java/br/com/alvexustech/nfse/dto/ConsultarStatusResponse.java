package br.com.alvexustech.nfse.dto;

import br.com.alvexustech.nfse.domain.EmissionStatus;

public record ConsultarStatusResponse(
        EmissionStatus status,
        String protocolo,
        String chaveAcesso,
        String mensagem
) {
}
