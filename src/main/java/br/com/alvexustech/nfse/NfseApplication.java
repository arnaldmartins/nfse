package br.com.alvexustech.nfse;

import br.com.alvexustech.nfse.config.NationalApiProperties;
import br.com.alvexustech.nfse.config.NfseCertificateProperties;
import br.com.alvexustech.nfse.config.NfseEmissionProperties;
import br.com.alvexustech.nfse.config.NfseMunicipioProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        NfseCertificateProperties.class,
        NfseMunicipioProperties.class,
        NationalApiProperties.class,
        NfseEmissionProperties.class
})
public class NfseApplication {

    public static void main(String[] args) {
        SpringApplication.run(NfseApplication.class, args);
    }
}
