package br.com.alvexustech.nfse.service;

import br.com.alvexustech.nfse.repository.NfseDpsSequenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NfseDpsSequenceServiceTest {

    @Mock
    private NfseDpsSequenceRepository repository;
    @InjectMocks
    private NfseDpsSequenceService service;

    @Test
    void returnsTheNumberAllocatedByTheAtomicSequenceOperation() {
        when(repository.allocateNext(any(), eq("66375620000113"), eq(1), anyLong()))
                .thenReturn(Optional.of(1L));

        NfseDpsSequenceService.AllocatedDps allocated = service.allocateNext("66375620000113", "1");

        assertThat(allocated.serial()).isEqualTo(1);
        assertThat(allocated.number()).isEqualTo(1L);
    }
}
