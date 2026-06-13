package br.com.alvexustech.nfse.dto;

import jakarta.validation.constraints.NotBlank;

public record PrestadorDto(
        @NotBlank String cnpj,
        String inscricaoMunicipal
) {
}
