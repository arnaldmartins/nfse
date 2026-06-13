package br.com.alvexustech.nfse.repository;

import br.com.alvexustech.nfse.persistence.NfseEmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NfseEmissionRepository extends JpaRepository<NfseEmissionEntity, UUID> {

    Optional<NfseEmissionEntity> findByIdempotencyKey(String idempotencyKey);
}
