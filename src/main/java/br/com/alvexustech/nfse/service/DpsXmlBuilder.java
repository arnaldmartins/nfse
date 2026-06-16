package br.com.alvexustech.nfse.service;

import br.com.alvexustech.nfse.config.NfseMunicipioProperties;
import br.com.alvexustech.nfse.config.NfseEmissionProperties;
import br.com.alvexustech.nfse.dto.EmitirNfseRequest;
import org.springframework.stereotype.Component;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.zip.CRC32;

@Component
public class DpsXmlBuilder {

    private static final String NFSE_NAMESPACE = "http://www.sped.fazenda.gov.br/nfse";
    private static final DateTimeFormatter DPS_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final NfseMunicipioProperties municipioProperties;
    private final NfseEmissionProperties emissionProperties;

    public DpsXmlBuilder(NfseMunicipioProperties municipioProperties, NfseEmissionProperties emissionProperties) {
        this.municipioProperties = municipioProperties;
        this.emissionProperties = emissionProperties;
    }

    public DpsXml build(EmitirNfseRequest request) {
        try {
            String serie = stripLeadingZeros(onlyDigits(emissionProperties.serieDps()));
            String serieId = leftPadDigits(serie, 5);
            String numeroDps = numeroDps(request);
            String id = buildDpsId(request, serieId, numeroDps);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            var document = factory.newDocumentBuilder().newDocument();

            var dps = document.createElementNS(NFSE_NAMESPACE, "DPS");
            dps.setAttribute("versao", emissionProperties.leiauteVersao());
            document.appendChild(dps);

            var infDps = append(document, dps, "infDPS", null);
            infDps.setAttribute("Id", id);
            append(document, infDps, "tpAmb", emissionProperties.ambienteCodigo());
            append(document, infDps, "dhEmi", OffsetDateTime.now(ZoneId.of("America/Sao_Paulo")).format(DPS_DATE_TIME));
            append(document, infDps, "verAplic", emissionProperties.versaoAplicativo());
            append(document, infDps, "serie", serie);
            append(document, infDps, "nDPS", numeroDps);
            append(document, infDps, "dCompet", request.competencia().format(DateTimeFormatter.ISO_DATE));
            append(document, infDps, "tpEmit", "1");
            append(document, infDps, "cLocEmi", municipioProperties.codigoIbge());

            appendPrestador(document, infDps, request);
            appendTomador(document, infDps, request);
            appendServico(document, infDps, request);
            appendValores(document, infDps, request);

            return new DpsXml(id, serialize(document));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao montar XML DPS", ex);
        }
    }

    private void appendPrestador(org.w3c.dom.Document document, org.w3c.dom.Element infDps, EmitirNfseRequest request) {
        var prest = append(document, infDps, "prest", null);
        appendCpfCnpj(document, prest, request.prestador().cnpj());
        appendIfNotBlank(document, prest, "IM", request.prestador().inscricaoMunicipal());

        var regTrib = append(document, prest, "regTrib", null);
        append(document, regTrib, "opSimpNac", emissionProperties.optanteSimplesNacional());
        append(document, regTrib, "regApTribSN", emissionProperties.regimeApTributacaoSimplesNacional());
        append(document, regTrib, "regEspTrib", emissionProperties.regimeEspecialTributario());
    }

    private void appendTomador(org.w3c.dom.Document document, org.w3c.dom.Element infDps, EmitirNfseRequest request) {
        var toma = append(document, infDps, "toma", null);
        String cpf = onlyDigits(request.tomador().cpf());
        if (cpf.length() != 11) {
            throw new IllegalArgumentException("CPF do tomador deve conter 11 digitos");
        }
        append(document, toma, "CPF", cpf);
        append(document, toma, "xNome", request.tomador().nome());
        appendIfNotBlank(document, toma, "email", request.tomador().email());
    }

    private void appendServico(org.w3c.dom.Document document, org.w3c.dom.Element infDps, EmitirNfseRequest request) {
        var serv = append(document, infDps, "serv", null);
        var locPrest = append(document, serv, "locPrest", null);
        append(document, locPrest, "cLocPrestacao", municipioProperties.codigoIbge());

        var cServ = append(document, serv, "cServ", null);
        append(document, cServ, "cTribNac", normalizeCodigoTributacaoNacional(request.servico().codigoServicoNacional()));
        appendCodigoTributacaoMunicipal(document, cServ, request.servico().codigoTributacaoMunicipal());
        append(document, cServ, "xDescServ", request.servico().descricao());
    }

    private void appendValores(org.w3c.dom.Document document, org.w3c.dom.Element infDps, EmitirNfseRequest request) {
        var valores = append(document, infDps, "valores", null);
        var vServPrest = append(document, valores, "vServPrest", null);
        append(document, vServPrest, "vServ", decimal(request.servico().valor()));

        var trib = append(document, valores, "trib", null);
        var tribMun = append(document, trib, "tribMun", null);
        append(document, tribMun, "tribISSQN", emissionProperties.tributacaoIssqn());
        append(document, tribMun, "tpRetISSQN", emissionProperties.tipoRetencaoIssqn());

        var totTrib = append(document, trib, "totTrib", null);
        append(document, totTrib, "pTotTribSN", emissionProperties.indicadorTotalTributos());
    }

    private void appendCpfCnpj(org.w3c.dom.Document document, org.w3c.dom.Element parent, String documento) {
        String digits = onlyDigits(documento);
        if (digits.length() == 11) {
            append(document, parent, "CPF", digits);
            return;
        }
        if (digits.length() == 14) {
            append(document, parent, "CNPJ", digits);
            return;
        }
        throw new IllegalArgumentException("Documento deve conter CPF com 11 digitos ou CNPJ com 14 digitos");
    }

    private void appendIfNotBlank(org.w3c.dom.Document document, org.w3c.dom.Element parent, String name, String value) {
        if (value != null && !value.isBlank()) {
            append(document, parent, name, value);
        }
    }

    private org.w3c.dom.Element append(org.w3c.dom.Document document, org.w3c.dom.Element parent, String name, String value) {
        var element = document.createElementNS(NFSE_NAMESPACE, name);
        if (value != null) {
            element.setTextContent(value);
        }
        parent.appendChild(element);
        return element;
    }

    private void appendCodigoTributacaoMunicipal(org.w3c.dom.Document document, org.w3c.dom.Element parent, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String digits = onlyDigits(value);
        if (digits.length() == 4 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.length() != 3) {
            throw new IllegalArgumentException("codigoTributacaoMunicipal deve conter 3 digitos conforme XSD TCCodTribMun");
        }
        append(document, parent, "cTribMun", digits);
    }

    private String normalizeCodigoTributacaoNacional(String value) {
        String digits = onlyDigits(value);
        if (digits.length() == 4) {
            return digits + "00";
        }
        if (digits.length() == 6) {
            return digits;
        }
        throw new IllegalArgumentException("codigoServicoNacional deve conter 6 digitos, ou item/subitem com 4 digitos");
    }

    private String buildDpsId(EmitirNfseRequest request, String serie, String numeroDps) {
        String documento = onlyDigits(request.prestador().cnpj());
        String tipoInscricao = documento.length() == 11 ? "1" : "2";
        String inscricao = documento.length() == 11 ? leftPadDigits(documento, 14) : documento;
        return "DPS" + municipioProperties.codigoIbge() + tipoInscricao + inscricao + serie + leftPadDigits(numeroDps, 15);
    }

    private String numeroDps(EmitirNfseRequest request) {
        String digits = onlyDigits(request.idempotencyKey());
        if (!digits.isBlank()) {
            String normalized = stripLeadingZeros(digits);
            if (normalized.length() <= 15) {
                return normalized;
            }
            return normalized.substring(normalized.length() - 15);
        }

        CRC32 crc32 = new CRC32();
        crc32.update(request.idempotencyKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Long.toString(crc32.getValue() + 1L);
    }

    private String stripLeadingZeros(String value) {
        String stripped = value.replaceFirst("^0+", "");
        return stripped.isBlank() ? "1" : stripped;
    }

    private String decimal(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String leftPadDigits(String value, int size) {
        String digits = stripLeadingZeros(onlyDigits(value));
        if (digits.length() > size) {
            throw new IllegalArgumentException("Valor numerico excede " + size + " digitos");
        }
        return "0".repeat(size - digits.length()) + digits;
    }

    private String serialize(org.w3c.dom.Document document) throws Exception {
        StringWriter writer = new StringWriter();
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString().replaceAll(">\\s+<", "><");
    }

    public record DpsXml(String id, String xml) {
    }
}
