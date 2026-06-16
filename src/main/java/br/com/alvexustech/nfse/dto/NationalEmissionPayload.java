package br.com.alvexustech.nfse.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NationalEmissionPayload(
        @JsonProperty("dpsXmlGZipB64")
        String dpsXmlGZipB64
) {
}
