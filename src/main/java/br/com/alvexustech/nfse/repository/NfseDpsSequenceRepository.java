package br.com.alvexustech.nfse.repository;

import br.com.alvexustech.nfse.persistence.NfseDpsSequenceEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NfseDpsSequenceRepository extends JpaRepository<NfseDpsSequenceEntity, UUID> {

    Optional<NfseDpsSequenceEntity> findByIssuerCnpjAndDpsSerial(String issuerCnpj, Integer dpsSerial);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from NfseDpsSequenceEntity s where s.issuerCnpj = :issuerCnpj and s.dpsSerial = :dpsSerial")
    Optional<NfseDpsSequenceEntity> findByIssuerCnpjAndDpsSerialForUpdate(
            @Param("issuerCnpj") String issuerCnpj,
            @Param("dpsSerial") Integer dpsSerial);

    @Query(value = """
            insert into nfse_dps_sequence (
                id, issuer_cnpj, dps_serial, last_dps_issued, created_at, updated_at
            ) values (
                :id, :issuerCnpj, :dpsSerial, 1, current_timestamp, current_timestamp
            )
            on conflict (issuer_cnpj, dps_serial) do update
            set last_dps_issued = nfse_dps_sequence.last_dps_issued + 1,
                updated_at = current_timestamp
            where nfse_dps_sequence.last_dps_issued < :maxDpsNumber
            returning last_dps_issued
            """, nativeQuery = true)
    Optional<Long> allocateNext(
            @Param("id") UUID id,
            @Param("issuerCnpj") String issuerCnpj,
            @Param("dpsSerial") Integer dpsSerial,
            @Param("maxDpsNumber") Long maxDpsNumber);
}
