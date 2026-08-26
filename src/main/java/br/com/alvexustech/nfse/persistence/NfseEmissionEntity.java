package br.com.alvexustech.nfse.persistence;

import br.com.alvexustech.nfse.domain.EmissionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nfse_emission")
public class NfseEmissionEntity {

    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 120)
    private String idempotencyKey;
    @Column(nullable = false, length = 40)
    private String provider;
    @Column(nullable = false, length = 7)
    private String municipalityCode;
    @Column(length = 120)
    private String providerProtocol;
    @Column(length = 80)
    private String accessKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EmissionStatus status;
    @Column(nullable = false, length = 14)
    private String issuerCnpj;
    @Column(nullable = false, length = 20)
    private String serviceTakerDocument;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal serviceAmount;
    @Column(columnDefinition = "TEXT")
    private String dpsXml;
    @Column(columnDefinition = "TEXT")
    private String signedXml;
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(precision = 5, scale = 0)
    private Integer dpsSerial;
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(precision = 15, scale = 0)
    private Long dpsNumber;
    @Column(columnDefinition = "TEXT")
    private String responsePayload;
    @Column(length = 80)
    private String errorCode;
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    @Column(nullable = false)
    private OffsetDateTime createdAt;
    @Column(nullable = false)
    private OffsetDateTime updatedAt;
    private OffsetDateTime emittedAt;

    protected NfseEmissionEntity() {
    }

    public NfseEmissionEntity(UUID id, String idempotencyKey, String provider, String municipalityCode,
                              EmissionStatus status, String issuerCnpj, String serviceTakerDocument,
                              BigDecimal serviceAmount) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.provider = provider;
        this.municipalityCode = municipalityCode;
        this.status = status;
        this.issuerCnpj = issuerCnpj;
        this.serviceTakerDocument = serviceTakerDocument;
        this.serviceAmount = serviceAmount;
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
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getProvider() { return provider; }
    public String getMunicipalityCode() { return municipalityCode; }
    public String getProviderProtocol() { return providerProtocol; }
    public String getAccessKey() { return accessKey; }
    public EmissionStatus getStatus() { return status; }
    public String getIssuerCnpj() { return issuerCnpj; }
    public String getServiceTakerDocument() { return serviceTakerDocument; }
    public BigDecimal getServiceAmount() { return serviceAmount; }
    public String getDpsXml() { return dpsXml; }
    public String getSignedXml() { return signedXml; }
    public Integer getDpsSerial() { return dpsSerial; }
    public Long getDpsNumber() { return dpsNumber; }
    public String getResponsePayload() { return responsePayload; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getEmittedAt() { return emittedAt; }

    public void setDpsXml(String dpsXml) { this.dpsXml = dpsXml; }
    public void setSignedXml(String signedXml) { this.signedXml = signedXml; }
    public void setDpsNumber(Integer dpsSerial, Long dpsNumber) {
        this.dpsSerial = dpsSerial;
        this.dpsNumber = dpsNumber;
    }
    public void setResponsePayload(String responsePayload) { this.responsePayload = responsePayload; }
    public void setProviderProtocol(String providerProtocol) { this.providerProtocol = providerProtocol; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public void setStatus(EmissionStatus status) { this.status = status; }
    public void setError(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
    public void markAuthorized(String accessKey) {
        this.status = EmissionStatus.AUTHORIZED;
        this.accessKey = accessKey;
        this.emittedAt = OffsetDateTime.now();
    }
}
