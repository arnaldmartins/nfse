package br.com.alvexustech.nfse.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record EmitirNfseRequest(
        @NotBlank String idempotencyKey,
        @NotNull LocalDate competencia,
        @Valid @NotNull PrestadorDto prestador,
        @Valid @NotNull TomadorDto tomador,
        @Valid @NotNull ServicoDto servico
) {
}
