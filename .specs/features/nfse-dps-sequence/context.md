# NFS-e DPS Sequence Context

**Gathered:** 2026-07-07
**Spec:** `.specs/features/nfse-dps-sequence/spec.md`
**Status:** Ready for design

---

## Feature Boundary

This feature adds persistent DPS sequence storage and uses that sequence during new NFS-e emission preparation. It does not add sequence administration endpoints, provider reconciliation, or lifecycle operations beyond the existing issuance flow.

---

## Implementation Decisions

### Backend State and Concurrency

- Use the existing Spring Data JPA and Flyway stack.
- Serialize allocation per `(issuer_cnpj, dps_serial)` using a database-backed lock strategy.
- Preserve the existing `idempotencyKey` lookup as the first step in emission preparation.
- Treat provider rejection or provider failure after a signed DPS exists as a consumed DPS number.

### Fiscal Number Source

- Continue sourcing the DPS series from `nfse.emission.serie-dps`.
- Normalize `prestador.cnpj` to 14 digits before using it as `issuer_cnpj`.
- Replace the current `DpsXmlBuilder.numeroDps(idempotencyKey)` behavior with an allocated sequence number supplied by the emission service.

### Agent's Discretion

- Choose exact Java class names that fit existing repository/entity/service naming.
- Choose unit versus integration test split as long as each acceptance criterion has coverage.

### Declined / Undiscussed Gray Areas -> Assumptions

- No user decisions were gathered interactively. The assumptions in `spec.md` are the defaults for table name, sequence scope, initial counter value, nullability, failure semantics, and audit fields.

---

## Specific References

- Project baseline: `docs/PROJECT_KNOWLEDGE_BASE.md`.
- Current sequence-like behavior: `DpsXmlBuilder` derives `nDPS` from `idempotencyKey`; this feature replaces that behavior.

---

## Deferred Ideas

- Manual API to seed or adjust sequence values.
- Recovery workflow to reconcile local sequence state with provider-side DPS/NFS-e state.
- Sequence history/audit table beyond current value and emission audit fields.
