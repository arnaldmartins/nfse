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
        Duration idempotencyWindow,
        String schemaPath,
        String leiauteVersao,
        String versaoAplicativo,
        String serieDps,
        String optanteSimplesNacional,
        String regimeApTributacaoSimplesNacional,
        String regimeEspecialTributario,
        String tributacaoIssqn,
        String tipoRetencaoIssqn,
        String indicadorTotalTributos
) {
    public NfseEmissionProperties {
        schemaPath = defaultIfBlank(schemaPath, "xsd/nfse-v1.01/DPS_v1.01.xsd");
        leiauteVersao = defaultIfBlank(leiauteVersao, "1.01");
        versaoAplicativo = defaultIfBlank(versaoAplicativo, "alvexus-nfse-1.0");
        serieDps = defaultIfBlank(serieDps, "1");
        optanteSimplesNacional = defaultIfBlank(optanteSimplesNacional, "1");
        regimeEspecialTributario = defaultIfBlank(regimeEspecialTributario, "0");
        tributacaoIssqn = defaultIfBlank(tributacaoIssqn, "1");
        tipoRetencaoIssqn = defaultIfBlank(tipoRetencaoIssqn, "1");
        indicadorTotalTributos = defaultIfBlank(indicadorTotalTributos, "0");
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
