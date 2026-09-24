package br.com.alvexustech.nfse.service;

import br.com.alvexustech.nfse.config.NfseEmissionProperties;
import br.com.alvexustech.nfse.config.NfseMunicipioProperties;
import br.com.alvexustech.nfse.dto.EmitirNfseRequest;
import br.com.alvexustech.nfse.dto.PrestadorDto;
import br.com.alvexustech.nfse.dto.ServicoDto;
import br.com.alvexustech.nfse.dto.TomadorDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DpsXmlBuilderTest {

    private final NfseEmissionProperties emissionProperties = new NfseEmissionProperties(
            "nacional",
            "2",
            Duration.ofHours(24),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
    );
    private final NfseMunicipioProperties municipioProperties = new NfseMunicipioProperties("3106200", "Belo Horizonte", "MG");
    private final DpsXmlBuilder builder = new DpsXmlBuilder(municipioProperties, emissionProperties);
    private final NfseXmlValidator validator = new NfseXmlValidator(emissionProperties);

    @Test
    void buildsDpsXmlValidAgainstOfficialSchema() {
        EmitirNfseRequest request = new EmitirNfseRequest(
                "pedido-812345-unico",
                LocalDate.of(2025, 6, 16),
                new PrestadorDto("66375620000113", ""),
                new TomadorDto("11905650647", "Arnald Alves Martins", "arnaldalvesmartins@gmail.com"),
                new ServicoDto("01.01", "Desenvolvimento de software", new BigDecimal("1000.00"), "0101")
        );

        DpsXmlBuilder.DpsXml dpsXml = builder.build(request);

        validator.validateDps(dpsXml.xml());
        assertThat(dpsXml.id()).matches("DPS\\d{42}");
        assertThat(dpsXml.xml()).contains("<cTribNac>010100</cTribNac>");
        assertThat(dpsXml.xml()).contains("<cTribMun>101</cTribMun>");
        assertThat(dpsXml.xml()).contains("<vServ>1000.00</vServ>");
        assertThat(dpsXml.xml()).contains("<CPF>11905650647</CPF>");
    }

    @Test
    void buildsDpsXmlWithAllocatedSerialAndNumber() {
        EmitirNfseRequest request = new EmitirNfseRequest(
                "pedido-sem-numero-fiscal",
                LocalDate.of(2025, 6, 16),
                new PrestadorDto("66375620000113", ""),
                new TomadorDto("11905650647", "Arnald Alves Martins", "arnaldalvesmartins@gmail.com"),
                new ServicoDto("01.01", "Desenvolvimento de software", new BigDecimal("1000.00"), "0101")
        );

        DpsXmlBuilder.DpsXml dpsXml = builder.build(request, 7, 42L);

        validator.validateDps(dpsXml.xml());
        assertThat(dpsXml.xml()).contains("<serie>7</serie>");
        assertThat(dpsXml.xml()).contains("<nDPS>42</nDPS>");
        assertThat(dpsXml.id()).endsWith("00007000000000000042");
        assertThat(dpsXml.idWithoutPrefix()).matches("\\d{42}");
        assertThat(dpsXml.idWithoutPrefix()).isEqualTo(dpsXml.id().substring(3));
    }

    @Test
    void extractsIdWithoutPrefixSafely() {
        DpsXmlBuilder.DpsXml standardDps = new DpsXmlBuilder.DpsXml("DPS310620026637562000011300007000000000000042", "<DPS/>");
        assertThat(standardDps.idWithoutPrefix()).isEqualTo("310620026637562000011300007000000000000042");

        DpsXmlBuilder.DpsXml nonPrefixedDps = new DpsXmlBuilder.DpsXml("CUSTOM12345", "<DPS/>");
        assertThat(nonPrefixedDps.idWithoutPrefix()).isEqualTo("CUSTOM12345");

        DpsXmlBuilder.DpsXml nullDps = new DpsXmlBuilder.DpsXml(null, "<DPS/>");
        assertThat(nullDps.idWithoutPrefix()).isNull();
    }
}
