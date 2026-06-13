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
import java.time.format.DateTimeFormatter;

@Component
public class DpsXmlBuilder {

    private final NfseMunicipioProperties municipioProperties;
    private final NfseEmissionProperties emissionProperties;

    public DpsXmlBuilder(NfseMunicipioProperties municipioProperties, NfseEmissionProperties emissionProperties) {
        this.municipioProperties = municipioProperties;
        this.emissionProperties = emissionProperties;
    }

    public DpsXml build(EmitirNfseRequest request) {
        try {
            String id = "DPS" + request.prestador().cnpj() + request.idempotencyKey().replaceAll("[^A-Za-z0-9]", "");
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            var dps = document.createElement("DPS");
            dps.setAttribute("Id", id);
            dps.setAttribute("xmlns", "http://www.sped.fazenda.gov.br/nfse");
            document.appendChild(dps);

            append(document, dps, "infDPS", null);
            var infDps = (org.w3c.dom.Element) dps.getElementsByTagName("infDPS").item(0);
            append(document, infDps, "tpAmb", emissionProperties.ambienteCodigo());
            append(document, infDps, "dhEmi", request.competencia().format(DateTimeFormatter.ISO_DATE));
            append(document, infDps, "cLocEmi", municipioProperties.codigoIbge());
            append(document, infDps, "CNPJPrest", request.prestador().cnpj());
            append(document, infDps, "IMPrest", request.prestador().inscricaoMunicipal());
            append(document, infDps, "docTomador", request.tomador().documento());
            append(document, infDps, "xNomeTomador", request.tomador().nome());
            append(document, infDps, "cServ", request.servico().codigoServicoNacional());
            append(document, infDps, "xDescServ", request.servico().descricao());
            append(document, infDps, "vServ", request.servico().valor().toPlainString());

            return new DpsXml(id, serialize(document));
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao montar XML DPS", ex);
        }
    }

    private void append(org.w3c.dom.Document document, org.w3c.dom.Element parent, String name, String value) {
        var element = document.createElement(name);
        if (value != null) {
            element.setTextContent(value);
        }
        parent.appendChild(element);
    }

    private String serialize(org.w3c.dom.Document document) throws Exception {
        StringWriter writer = new StringWriter();
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    public record DpsXml(String id, String xml) {
    }
}
