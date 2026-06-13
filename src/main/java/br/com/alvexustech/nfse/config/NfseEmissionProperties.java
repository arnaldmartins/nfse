package br.com.alvexustech.nfse.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "nfse.emission")
public record NfseEmissionProperties(
        @NotBlank String defaultProvider,
        @NotBlank String ambienteCodigo,
        Duration idempotencyWindow
) {
}
