package br.com.alvexustech.nfse.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "nfse.national-api")
public record NationalApiProperties(
        @NotBlank String baseUrl,
        @NotBlank String emissionPath,
        @NotBlank String statusPathTemplate,
        @NotBlank String dpsPathTemplate,
        Duration connectTimeout,
        Duration responseTimeout,
        DataSize maxInMemorySize
) {
}
