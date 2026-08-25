CREATE TABLE nfse_dps_sequence (
    id UUID PRIMARY KEY,
    issuer_cnpj VARCHAR(14) NOT NULL,
    dps_serial NUMERIC(5, 0) NOT NULL,
    last_dps_issued NUMERIC(15, 0) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_nfse_dps_sequence_issuer_serial UNIQUE (issuer_cnpj, dps_serial),
    CONSTRAINT chk_nfse_dps_sequence_last_non_negative CHECK (last_dps_issued >= 0)
);

ALTER TABLE nfse_emission
    ADD COLUMN dps_serial NUMERIC(5, 0),
    ADD COLUMN dps_number NUMERIC(15, 0);

CREATE INDEX idx_nfse_emission_issuer_dps
    ON nfse_emission (issuer_cnpj, dps_serial, dps_number);
