package br.com.alvexustech.nfse.service;

import br.com.alvexustech.nfse.repository.NfseDpsSequenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NfseDpsSequenceServiceTest {

    @Mock
    private NfseDpsSequenceRepository repository;
    @InjectMocks
    private NfseDpsSequenceService service;

    @Test
    void surfacesControlledFailureWhenFirstRowCreationRaces() {
        when(repository.findByIssuerCnpjAndDpsSerialForUpdate("66375620000113", 1))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate issuer serial"));

        assertThatThrownBy(() -> service.allocateNext("66375620000113", "1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Concorrencia ao criar sequencia DPS; tente novamente")
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }
}
