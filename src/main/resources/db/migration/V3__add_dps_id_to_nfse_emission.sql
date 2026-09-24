ALTER TABLE nfse_emission
    ADD COLUMN dps_id VARCHAR(50);

CREATE INDEX idx_nfse_emission_dps_id
    ON nfse_emission (dps_id);
