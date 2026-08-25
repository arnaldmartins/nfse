package br.com.alvexustech.nfse.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nfse_dps_sequence")
public class NfseDpsSequenceEntity {

    @Id
    private UUID id;
    @Column(nullable = false, length = 14)
    private String issuerCnpj;
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(nullable = false, precision = 5, scale = 0)
    private Integer dpsSerial;
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(nullable = false, precision = 15, scale = 0)
    private Long lastDpsIssued;
    @Column(nullable = false)
    private OffsetDateTime createdAt;
    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected NfseDpsSequenceEntity() {
    }

    public NfseDpsSequenceEntity(UUID id, String issuerCnpj, Integer dpsSerial, Long lastDpsIssued) {
        this.id = id;
        this.issuerCnpj = issuerCnpj;
        this.dpsSerial = dpsSerial;
        this.lastDpsIssued = lastDpsIssued;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getIssuerCnpj() { return issuerCnpj; }
    public Integer getDpsSerial() { return dpsSerial; }
    public Long getLastDpsIssued() { return lastDpsIssued; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setLastDpsIssued(Long lastDpsIssued) {
        this.lastDpsIssued = lastDpsIssued;
    }
}
