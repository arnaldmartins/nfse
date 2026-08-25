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
}
