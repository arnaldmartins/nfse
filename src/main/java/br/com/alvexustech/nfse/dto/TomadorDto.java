package br.com.alvexustech.nfse.dto;

import jakarta.validation.constraints.NotBlank;

public record TomadorDto(
        @NotBlank String documento,
        @NotBlank String nome,
        String email
) {
}
