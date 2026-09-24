package br.com.alvexustech.nfse.repository;

import br.com.alvexustech.nfse.PostgresIntegrationTest;
import br.com.alvexustech.nfse.domain.EmissionStatus;
import br.com.alvexustech.nfse.persistence.NfseEmissionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NfseEmissionRepositoryTest extends PostgresIntegrationTest {

    @Autowired
    private NfseEmissionRepository repository;

    @Test
    void storesGeneratedDpsSerialAndNumberOnEmission() {
        NfseEmissionEntity emission = emission("pedido-com-dps-audit");
        emission.setDpsNumber(7, 42L);
        emission.setDpsId("310620026637562000011300007000000000000042");

        NfseEmissionEntity saved = repository.saveAndFlush(emission);

        NfseEmissionEntity found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getDpsSerial()).isEqualTo(7);
        assertThat(found.getDpsNumber()).isEqualTo(42L);
        assertThat(found.getDpsId()).isEqualTo("310620026637562000011300007000000000000042");
    }

    @Test
    void readsLegacyEmissionWithNullDpsAuditFields() {
        NfseEmissionEntity saved = repository.saveAndFlush(emission("pedido-sem-dps-audit"));

        NfseEmissionEntity found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getDpsSerial()).isNull();
        assertThat(found.getDpsNumber()).isNull();
        assertThat(found.getDpsId()).isNull();
    }

    private NfseEmissionEntity emission(String idempotencyKey) {
        return new NfseEmissionEntity(
                UUID.randomUUID(),
                idempotencyKey,
                "nacional",
                "3106200",
                EmissionStatus.RECEIVED,
                "66375620000113",
                "11905650647",
                new BigDecimal("1000.00")
        );
    }
}
