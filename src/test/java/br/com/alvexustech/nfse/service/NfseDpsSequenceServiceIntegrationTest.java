package br.com.alvexustech.nfse.service;

import br.com.alvexustech.nfse.PostgresIntegrationTest;
import br.com.alvexustech.nfse.persistence.NfseDpsSequenceEntity;
import br.com.alvexustech.nfse.repository.NfseDpsSequenceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    @Autowired
    private EntityManager entityManager;

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

        entityManager.clear();
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

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void allocatesDistinctConsecutiveNumbersForConcurrentRequestsWithTheSameKey() throws Exception {
        repository.saveAndFlush(new NfseDpsSequenceEntity(UUID.randomUUID(), "66375620000113", 4, 0L));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<NfseDpsSequenceService.AllocatedDps>> allocations = List.of(
                    executor.submit(allocateWhenStarted(ready, start, "66375620000113", "4")),
                    executor.submit(allocateWhenStarted(ready, start, "66375620000113", "4")));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(allocations.stream().map(this::getAllocation).map(NfseDpsSequenceService.AllocatedDps::number))
                    .containsExactlyInAnyOrder(1L, 2L);
            assertThat(repository.findByIssuerCnpjAndDpsSerial("66375620000113", 4).orElseThrow().getLastDpsIssued())
                    .isEqualTo(2L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void allocatesDistinctConsecutiveNumbersWhenConcurrentRequestsCreateTheFirstRow() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<NfseDpsSequenceService.AllocatedDps>> allocations = List.of(
                    executor.submit(allocateWhenStarted(ready, start, "66375620000113", "7")),
                    executor.submit(allocateWhenStarted(ready, start, "66375620000113", "7")));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(allocations.stream().map(this::getAllocation).map(NfseDpsSequenceService.AllocatedDps::number))
                    .containsExactlyInAnyOrder(1L, 2L);
            assertThat(repository.findByIssuerCnpjAndDpsSerial("66375620000113", 7).orElseThrow().getLastDpsIssued())
                    .isEqualTo(2L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void keepsCountersIndependentForConcurrentDifferentIssuerAndSerialKeys() throws Exception {
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            List<Future<NfseDpsSequenceService.AllocatedDps>> allocations = List.of(
                    executor.submit(allocateWhenStarted(ready, start, "66375620000113", "5")),
                    executor.submit(allocateWhenStarted(ready, start, "66375620000113", "6")),
                    executor.submit(allocateWhenStarted(ready, start, "11444777000161", "5")));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(allocations.stream().map(this::getAllocation).map(NfseDpsSequenceService.AllocatedDps::number))
                    .containsExactly(1L, 1L, 1L);
            assertThat(repository.findByIssuerCnpjAndDpsSerial("66375620000113", 5).orElseThrow().getLastDpsIssued())
                    .isEqualTo(1L);
            assertThat(repository.findByIssuerCnpjAndDpsSerial("66375620000113", 6).orElseThrow().getLastDpsIssued())
                    .isEqualTo(1L);
            assertThat(repository.findByIssuerCnpjAndDpsSerial("11444777000161", 5).orElseThrow().getLastDpsIssued())
                    .isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<NfseDpsSequenceService.AllocatedDps> allocateWhenStarted(
            CountDownLatch ready, CountDownLatch start, String issuerCnpj, String dpsSerial) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start concurrent allocation");
            }
            return service.allocateNext(issuerCnpj, dpsSerial);
        };
    }

    private NfseDpsSequenceService.AllocatedDps getAllocation(
            Future<NfseDpsSequenceService.AllocatedDps> allocation) {
        try {
            return allocation.get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("Concurrent allocation failed", ex);
        }
    }
}
