package br.com.alvexustech.nfse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ServicoDto(
        @NotBlank String codigoServicoNacional,
        @NotBlank String descricao,
        @NotNull BigDecimal valor,
        String codigoTributacaoMunicipal
) {
}
