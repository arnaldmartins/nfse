# STATE

## Decisions

### AD-001
- **Decision**: Persist DPS sequence in dedicated table `nfse_dps_sequence` scoped by `(issuer_cnpj, dps_serial)`.
- **Reason**: Decouple fiscal sequential numbering from idempotency key and guarantee ordered DPS issuance.
- **Trade-off**: Requires database row lock during allocation inside emission transaction.
- **Scope**: `br.com.alvexustech.nfse.persistence`, `br.com.alvexustech.nfse.service`
- **Date**: 2026-09-23
- **Status**: active

### AD-002
- **Decision**: Store `dps_id` (42-character National DPS identifier without the "DPS" prefix) in `nfse_emission`.
- **Reason**: Enables direct lookup, audit, and provider traceability of the fiscal document by its national DPS identifier without parsing XML.
- **Trade-off**: Additional varchar column and index on `nfse_emission`.
- **Scope**: `nfse_emission` schema, `NfseEmissionEntity`, `DpsXmlBuilder`, `NfseEmissionService`
- **Date**: 2026-09-24
- **Status**: active

## Handoff

- **Feature**: .specs/features/nfse-emission-dps-id
- **Phase / Task**: Complete / Feature Validated
- **Completed**: T1, T2, T3, T4
- **In-progress**: none
- **Next step**: Ready for user review / deployment
- **Blockers**: none
- **Uncommitted files**: none
- **Branch**: main
