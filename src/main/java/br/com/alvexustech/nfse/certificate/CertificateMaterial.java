package br.com.alvexustech.nfse.certificate;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public record CertificateMaterial(
        KeyStore keyStore,
        char[] password,
        String alias,
        PrivateKey privateKey,
        X509Certificate certificate
) {
}
