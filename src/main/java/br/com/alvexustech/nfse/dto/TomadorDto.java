package br.com.alvexustech.nfse.dto;

import jakarta.validation.constraints.NotBlank;

public record TomadorDto(
        @NotBlank String cpf,
        @NotBlank String nome,
        String email
) {
}
