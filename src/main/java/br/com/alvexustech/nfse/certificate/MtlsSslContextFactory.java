package br.com.alvexustech.nfse.certificate;

import br.com.alvexustech.nfse.exception.CertificateLoadingException;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;

@Component
public class MtlsSslContextFactory {

    public SslContext create(CertificateMaterial material) {
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(material.keyStore(), material.password());

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);

            return SslContextBuilder.forClient()
                    .keyManager(keyManagerFactory)
                    .trustManager(trustManagerFactory)
                    .build();
        } catch (Exception ex) {
            throw new CertificateLoadingException("Falha ao criar contexto SSL mTLS", ex);
        }
    }
}
