package br.com.alvexustech.nfse.certificate;

import br.com.alvexustech.nfse.config.NfseCertificateProperties;
import br.com.alvexustech.nfse.exception.CertificateLoadingException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

@Component
public class A1CertificateLoader {

    private final NfseCertificateProperties properties;

    public A1CertificateLoader(NfseCertificateProperties properties) {
        this.properties = properties;
    }

    public CertificateMaterial load() {
        char[] password = properties.password().toCharArray();
        try (InputStream inputStream = Files.newInputStream(Path.of(properties.pfxPath()))) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(inputStream, password);

            String alias = resolveAlias(keyStore);
            Key key = keyStore.getKey(alias, password);
            if (!(key instanceof PrivateKey privateKey)) {
                throw new CertificateLoadingException("Alias do certificado A1 nao contem chave privada: " + alias);
            }

            Certificate certificate = keyStore.getCertificate(alias);
            if (!(certificate instanceof X509Certificate x509Certificate)) {
                throw new CertificateLoadingException("Certificado do alias nao e X509: " + alias);
            }

            return new CertificateMaterial(keyStore, password, alias, privateKey, x509Certificate);
        } catch (Exception ex) {
            throw new CertificateLoadingException("Falha ao carregar certificado A1 PKCS12", ex);
        }
    }

    private String resolveAlias(KeyStore keyStore) throws Exception {
        if (StringUtils.hasText(properties.alias())) {
            return properties.alias();
        }
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String candidate = aliases.nextElement();
            if (keyStore.isKeyEntry(candidate)) {
                return candidate;
            }
        }
        throw new CertificateLoadingException("Nenhum alias com chave privada encontrado no PFX");
    }
}
