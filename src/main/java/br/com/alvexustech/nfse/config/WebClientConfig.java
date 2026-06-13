package br.com.alvexustech.nfse.config;

import br.com.alvexustech.nfse.certificate.A1CertificateLoader;
import br.com.alvexustech.nfse.certificate.CertificateMaterial;
import br.com.alvexustech.nfse.certificate.MtlsSslContextFactory;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    CertificateMaterial certificateMaterial(A1CertificateLoader loader) {
        return loader.load();
    }

    @Bean
    WebClient nationalNfseWebClient(
            NationalApiProperties properties,
            CertificateMaterial certificateMaterial,
            MtlsSslContextFactory sslContextFactory
    ) {
        HttpClient httpClient = HttpClient.create()
                .secure(ssl -> ssl.sslContext(sslContextFactory.create(certificateMaterial)))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(properties.connectTimeout().toMillis()))
                .responseTimeout(properties.responseTimeout())
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(properties.responseTimeout().toSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(properties.responseTimeout().toSeconds(), TimeUnit.SECONDS)));

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs()
                        .maxInMemorySize(Math.toIntExact(properties.maxInMemorySize().toBytes())))
                .build();

        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }
}
