package br.com.alvexustech.nfse.dto;

public record NationalEmissionResponse(
        String protocolo,
        String chaveAcesso,
        String status,
        String mensagem,
        String rawPayload
) {
}
