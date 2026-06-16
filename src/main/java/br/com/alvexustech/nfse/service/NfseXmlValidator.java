package br.com.alvexustech.nfse.service;

import br.com.alvexustech.nfse.config.NfseEmissionProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Component
public class NfseXmlValidator {

    private final Schema dpsSchema;

    public NfseXmlValidator(NfseEmissionProperties properties) {
        this.dpsSchema = loadSchema(properties.schemaPath());
    }

    public void validateDps(String xml) {
        try {
            Validator validator = dpsSchema.newValidator();
            validator.validate(new StreamSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
        } catch (SAXParseException ex) {
            throw new IllegalArgumentException("XML DPS invalido no XSD: linha " + ex.getLineNumber()
                    + ", coluna " + ex.getColumnNumber() + " - " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new IllegalArgumentException("XML DPS invalido no XSD: " + ex.getMessage(), ex);
        }
    }

    private Schema loadSchema(String schemaPath) {
        try {
            ClassPathResource resource = new ClassPathResource(schemaPath);
            StreamSource source = new StreamSource(resource.getInputStream());
            source.setSystemId(resource.getURL().toExternalForm());
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar:file");
            return factory.newSchema(source);
        } catch (SAXException ex) {
            throw new IllegalStateException("Falha ao carregar schema XSD da DPS: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao carregar schema XSD da DPS", ex);
        }
    }
}
