package br.com.alvexustech.nfse.service;

import br.com.alvexustech.nfse.persistence.NfseDpsSequenceEntity;
import br.com.alvexustech.nfse.repository.NfseDpsSequenceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NfseDpsSequenceService {

    private static final long MAX_DPS_NUMBER = 999_999_999_999_999L;

    private final NfseDpsSequenceRepository repository;

    public NfseDpsSequenceService(NfseDpsSequenceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AllocatedDps allocateNext(String issuerCnpj, String configuredSerieDps) {
        String normalizedIssuerCnpj = normalizeIssuerCnpj(issuerCnpj);
        Integer dpsSerial = parseDpsSerial(configuredSerieDps);

        NfseDpsSequenceEntity sequence = repository
                .findByIssuerCnpjAndDpsSerialForUpdate(normalizedIssuerCnpj, dpsSerial)
                .orElseGet(() -> createFirstSequenceRow(normalizedIssuerCnpj, dpsSerial));

        Long lastDpsIssued = sequence.getLastDpsIssued();
        if (lastDpsIssued >= MAX_DPS_NUMBER) {
            throw new IllegalArgumentException("Numero DPS excede limite de 15 digitos");
        }

        long nextDpsNumber = lastDpsIssued + 1L;
        sequence.setLastDpsIssued(nextDpsNumber);
        return new AllocatedDps(dpsSerial, nextDpsNumber);
    }

    private NfseDpsSequenceEntity createFirstSequenceRow(String issuerCnpj, Integer dpsSerial) {
        try {
            return repository.saveAndFlush(new NfseDpsSequenceEntity(UUID.randomUUID(), issuerCnpj, dpsSerial, 0L));
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("Concorrencia ao criar sequencia DPS; tente novamente", ex);
        }
    }

    private String normalizeIssuerCnpj(String issuerCnpj) {
        String digits = onlyDigits(issuerCnpj);
        if (digits.length() != 14) {
            throw new IllegalArgumentException("CNPJ do prestador deve conter 14 digitos");
        }
        return digits;
    }

    private Integer parseDpsSerial(String configuredSerieDps) {
        String digits = onlyDigits(configuredSerieDps);
        if (digits.isBlank() || digits.length() > 5) {
            throw new IllegalArgumentException("serie-dps deve conter valor numerico de ate 5 digitos");
        }
        return Integer.parseInt(digits);
    }

    private String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    public record AllocatedDps(Integer serial, Long number) {
    }
}
