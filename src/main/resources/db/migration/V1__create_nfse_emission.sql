CREATE TABLE nfse_emission (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    provider VARCHAR(40) NOT NULL,
    municipality_code VARCHAR(7) NOT NULL,
    provider_protocol VARCHAR(120),
    access_key VARCHAR(80),
    status VARCHAR(40) NOT NULL,
    issuer_cnpj VARCHAR(14) NOT NULL,
    service_taker_document VARCHAR(20) NOT NULL,
    service_amount NUMERIC(15, 2) NOT NULL,
    dps_xml TEXT,
    signed_xml TEXT,
    response_payload TEXT,
    error_code VARCHAR(80),
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    emitted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_nfse_emission_status ON nfse_emission (status);
CREATE INDEX idx_nfse_emission_provider_protocol ON nfse_emission (provider_protocol);
