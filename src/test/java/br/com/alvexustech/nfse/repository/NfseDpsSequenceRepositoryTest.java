package br.com.alvexustech.nfse.repository;

import br.com.alvexustech.nfse.PostgresIntegrationTest;
import br.com.alvexustech.nfse.persistence.NfseDpsSequenceEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NfseDpsSequenceRepositoryTest extends PostgresIntegrationTest {

    @Autowired
    private NfseDpsSequenceRepository repository;

    @Test
    void findsLockedSequenceRowByIssuerCnpjAndDpsSerial() {
        NfseDpsSequenceEntity saved = repository.saveAndFlush(new NfseDpsSequenceEntity(
                UUID.randomUUID(), "66375620000113", 2, 41L));

        NfseDpsSequenceEntity locked = repository.findByIssuerCnpjAndDpsSerialForUpdate("66375620000113", 2)
                .orElseThrow();
        NfseDpsSequenceEntity found = repository.findByIssuerCnpjAndDpsSerial("66375620000113", 2)
                .orElseThrow();

        assertThat(locked.getId()).isEqualTo(saved.getId());
        assertThat(locked.getIssuerCnpj()).isEqualTo("66375620000113");
        assertThat(locked.getDpsSerial()).isEqualTo(2);
        assertThat(locked.getLastDpsIssued()).isEqualTo(41L);
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rejectsSecondSequenceRowForSameIssuerAndSerial() {
        repository.saveAndFlush(new NfseDpsSequenceEntity(
                UUID.randomUUID(), "66375620000113", 1, 0L));

        assertThatThrownBy(() -> repository.saveAndFlush(new NfseDpsSequenceEntity(
                UUID.randomUUID(), "66375620000113", 1, 1L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
