package br.com.alvexustech.nfse.signature;

import br.com.alvexustech.nfse.certificate.CertificateMaterial;
import br.com.alvexustech.nfse.exception.XmlSignatureException;
import org.apache.xml.security.Init;
import org.apache.xml.security.algorithms.MessageDigestAlgorithm;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.exceptions.XMLSecurityException;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.utils.Constants;
import org.apache.xml.security.utils.ElementProxy;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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
        try {
            ElementProxy.setDefaultPrefix(Constants.SignatureSpecNS, "");
        } catch (XMLSecurityException ex) {
            throw new ExceptionInInitializerError(ex);
        }
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
            markReferenceId(document, root, referenceId);

            XMLSignature signature = new XMLSignature(
                    document,
                    "",
                    XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
                    Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS
            );
            root.appendChild(signature.getElement());

            Transforms transforms = new Transforms(document);
            transforms.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE);
            transforms.addTransform(Transforms.TRANSFORM_C14N_OMIT_COMMENTS);

            String uri = referenceId == null || referenceId.isBlank() ? "" : "#" + referenceId;
            signature.addDocument(uri, transforms, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256);
            signature.addKeyInfo(certificateMaterial.certificate());
            signature.sign(certificateMaterial.privateKey());

            return serialize(document);
        } catch (Exception ex) {
            throw new XmlSignatureException("Falha ao assinar XML DPS/NFS-e", ex);
        }
    }

    private void markReferenceId(Document document, Element root, String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            return;
        }
        if (referenceId.equals(root.getAttribute("Id"))) {
            root.setIdAttribute("Id", true);
            return;
        }
        NodeList elements = document.getElementsByTagNameNS("*", "*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (referenceId.equals(element.getAttribute("Id"))) {
                element.setIdAttribute("Id", true);
                return;
            }
        }
        throw new IllegalArgumentException("Elemento XML com Id '" + referenceId + "' nao encontrado para assinatura");
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
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString().replace("&#13;", "");
    }
}
