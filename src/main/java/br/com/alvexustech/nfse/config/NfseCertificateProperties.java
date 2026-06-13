package br.com.alvexustech.nfse.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "nfse.certificate")
public record NfseCertificateProperties(
        @NotBlank String pfxPath,
        @NotBlank String password,
        String alias
) {
}
