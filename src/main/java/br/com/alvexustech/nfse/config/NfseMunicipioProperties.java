package br.com.alvexustech.nfse.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "nfse.municipio")
public record NfseMunicipioProperties(
        @NotBlank String codigoIbge,
        @NotBlank String nome,
        @NotBlank String uf
) {
}
