package br.com.alvexustech.nfse.signature;

import br.com.alvexustech.nfse.certificate.CertificateMaterial;
import br.com.alvexustech.nfse.exception.XmlSignatureException;
import org.apache.xml.security.Init;
import org.apache.xml.security.algorithms.MessageDigestAlgorithm;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

@Service
public class SantuarioXmlSignatureService implements XmlSignatureService {

    static {
        Init.init();
    }

    private final CertificateMaterial certificateMaterial;

    public SantuarioXmlSignatureService(CertificateMaterial certificateMaterial) {
        this.certificateMaterial = certificateMaterial;
    }

    @Override
    public String sign(String xml, String referenceId) {
        try {
            Document document = parse(xml);
            Element root = document.getDocumentElement();
            root.setIdAttribute("Id", true);

            XMLSignature signature = new XMLSignature(
                    document,
                    "",
                    XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
                    Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS
            );
            root.appendChild(signature.getElement());

            Transforms transforms = new Transforms(document);
            transforms.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE);
            transforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);

            String uri = referenceId == null || referenceId.isBlank() ? "" : "#" + referenceId;
            signature.addDocument(uri, transforms, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256);
            signature.addKeyInfo(certificateMaterial.certificate());
            signature.sign(certificateMaterial.privateKey());

            return serialize(document);
        } catch (Exception ex) {
            throw new XmlSignatureException("Falha ao assinar XML DPS/NFS-e", ex);
        }
    }

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String serialize(Document document) throws Exception {
        StringWriter writer = new StringWriter();
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }
}
