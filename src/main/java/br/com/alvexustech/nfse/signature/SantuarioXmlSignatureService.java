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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

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
                    Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS
            );
            Element signatureElement = signature.getElement();
            prepareSignatureWithoutPrefixes(signatureElement);
            root.appendChild(signatureElement);

            Transforms transforms = new Transforms(document);
            transforms.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE);
            transforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);

            String uri = referenceId == null || referenceId.isBlank() ? "" : "#" + referenceId;
            signature.addDocument(uri, transforms, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256);
            signature.addKeyInfo(certificateMaterial.certificate());
            signature.sign(certificateMaterial.privateKey());

            normalizeSignatureText(document);
            removeWhitespaceTextNodes(root);
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

    private void prepareSignatureWithoutPrefixes(Element signatureElement) {
        signatureElement.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", Constants.SignatureSpecNS);

        NodeList nodes = signatureElement.getElementsByTagNameNS("*", "*");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            removePrefixedNamespaceAttributes(element);
            if (element.getPrefix() != null && !element.getPrefix().isBlank()) {
                element.getOwnerDocument().renameNode(element, element.getNamespaceURI(), element.getLocalName());
            }
        }

        removePrefixedNamespaceAttributes(signatureElement);
        if (signatureElement.getPrefix() != null && !signatureElement.getPrefix().isBlank()) {
            signatureElement.getOwnerDocument().renameNode(
                    signatureElement, signatureElement.getNamespaceURI(), signatureElement.getLocalName());
        }
    }

    private void removePrefixedNamespaceAttributes(Element element) {
        for (int index = element.getAttributes().getLength() - 1; index >= 0; index--) {
            String attributeName = element.getAttributes().item(index).getNodeName();
            if (attributeName.startsWith("xmlns:")) {
                element.removeAttribute(attributeName);
            }
        }
    }

    private void normalizeSignatureText(Document document) {
        stripElementWhitespace(document, Constants.SignatureSpecNS, "SignatureValue");
        stripElementWhitespace(document, Constants.SignatureSpecNS, "X509Certificate");
    }

    private void stripElementWhitespace(Document document, String namespace, String localName) {
        NodeList nodes = document.getElementsByTagNameNS(namespace, localName);
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            String text = element.getTextContent();
            if (text != null && !text.isEmpty()) {
                element.setTextContent(text.replaceAll("\\s+", ""));
            }
        }
    }

    private void removeWhitespaceTextNodes(Element element) {
        NodeList children = element.getChildNodes();
        for (int index = children.getLength() - 1; index >= 0; index--) {
            Node child = children.item(index);
            if (child instanceof Text text && text.getData().strip().isEmpty()) {
                element.removeChild(child);
            } else if (child instanceof Element childElement) {
                removeWhitespaceTextNodes(childElement);
            }
        }
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
