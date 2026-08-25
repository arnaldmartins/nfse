package br.com.alvexustech.nfse.service;

import br.com.alvexustech.nfse.PostgresIntegrationTest;
import br.com.alvexustech.nfse.persistence.NfseDpsSequenceEntity;
import br.com.alvexustech.nfse.repository.NfseDpsSequenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(NfseDpsSequenceService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NfseDpsSequenceServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private NfseDpsSequenceService service;
    @Autowired
    private NfseDpsSequenceRepository repository;

    @Test
    void createsFirstSequenceRowWithNormalizedIssuerCnpjAndNumberOne() {
        NfseDpsSequenceService.AllocatedDps allocated = service.allocateNext("66.375.620/0001-13", "00001");

        NfseDpsSequenceEntity sequence = repository.findByIssuerCnpjAndDpsSerial("66375620000113", 1)
                .orElseThrow();
        assertThat(allocated.serial()).isEqualTo(1);
        assertThat(allocated.number()).isEqualTo(1L);
        assertThat(sequence.getIssuerCnpj()).isEqualTo("66375620000113");
        assertThat(sequence.getLastDpsIssued()).isEqualTo(1L);
    }

    @Test
    void incrementsExistingLockedSequenceRowAndPersistsNextNumber() {
        repository.saveAndFlush(new NfseDpsSequenceEntity(UUID.randomUUID(), "66375620000113", 2, 41L));

        NfseDpsSequenceService.AllocatedDps allocated = service.allocateNext("66375620000113", "2");

        NfseDpsSequenceEntity sequence = repository.findByIssuerCnpjAndDpsSerial("66375620000113", 2)
                .orElseThrow();
        assertThat(allocated.serial()).isEqualTo(2);
        assertThat(allocated.number()).isEqualTo(42L);
        assertThat(sequence.getLastDpsIssued()).isEqualTo(42L);
    }

    @Test
    void failsBeforeOverflowingFifteenDigitDpsLimit() {
        repository.saveAndFlush(new NfseDpsSequenceEntity(
                UUID.randomUUID(), "66375620000113", 3, 999_999_999_999_999L));

        assertThatThrownBy(() -> service.allocateNext("66375620000113", "3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Numero DPS excede limite de 15 digitos");
        assertThat(repository.findByIssuerCnpjAndDpsSerial("66375620000113", 3).orElseThrow().getLastDpsIssued())
                .isEqualTo(999_999_999_999_999L);
    }

    @Test
    void failsValidationBeforeCreatingSequenceRow() {
        assertThatThrownBy(() -> service.allocateNext("123", "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CNPJ do prestador deve conter 14 digitos");
        assertThatThrownBy(() -> service.allocateNext("66375620000113", "100000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("serie-dps deve conter valor numerico de ate 5 digitos");
        assertThat(repository.count()).isZero();
    }
}
