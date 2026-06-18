package br.com.alvexustech.nfse.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NationalEmissionResponse(
        @JsonProperty("tipoAmbiente") Integer tipoAmbiente,
        @JsonProperty("versaoAplicativo") String versaoAplicativo,
        @JsonProperty("dataHoraProcessamento") String dataHoraProcessamento,
        @JsonProperty("idDps") String idDps,
        @JsonProperty("chaveAcesso") String chaveAcesso,
        @JsonProperty("nfseXmlGZipB64") String nfseXmlGZipB64,
        @JsonProperty("alertas") List<Alerta> alertas,
        @JsonProperty("erro") Alerta erro,
        // Campos adicionais que podem vir em consultas ou outras chamadas
        @JsonProperty("protocolo") String protocolo,
        @JsonProperty("status") String status,
        @JsonProperty("mensagem") String mensagem
) {
    public record Alerta(
            @JsonProperty("codigo") String codigo,
            @JsonProperty("descricao") String descricao,
            @JsonProperty("complemento") String complemento
    ) {}
}
