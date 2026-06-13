package br.com.alvexustech.nfse.provider;

import br.com.alvexustech.nfse.exception.ProviderNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NfseProviderRegistry {

    private final Map<String, NfseProvider> providers;

    public NfseProviderRegistry(java.util.List<NfseProvider> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(NfseProvider::code, Function.identity()));
    }

    public NfseProvider get(String code) {
        NfseProvider provider = providers.get(code);
        if (provider == null) {
            throw new ProviderNotFoundException("Provider NFS-e nao encontrado: " + code);
        }
        return provider;
    }
}
