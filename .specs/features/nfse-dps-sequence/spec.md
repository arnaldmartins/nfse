# NFS-e DPS Sequence Specification

## Problem Statement

The current POC derives the DPS number from `idempotencyKey`, which is not a fiscal sequence and can collide with provider expectations for ordered DPS issuance. The system needs a persistent DPS sequence per issuer and DPS series, then must use the next sequence number when generating each new NFS-e DPS XML.

## Goals

- [ ] Persist DPS sequence state in a dedicated database table with the requested fiscal fields.
- [ ] Generate new DPS XML using the next persisted DPS number instead of deriving it from `idempotencyKey`.
- [ ] Preserve existing idempotency behavior so retries do not consume extra DPS numbers.
- [ ] Protect sequence increments from duplicate numbers under concurrent emission requests.

## Out of Scope

| Feature | Reason |
| ------- | ------ |
| Provider-side reconciliation of already issued numbers | Requires external provider lookup and recovery policy not requested here. |
| Manual sequence administration API | The feature only requires storage and automated use during issuance. |
| Multi-tenant issuer onboarding | Issuer identity already comes from `prestador.cnpj` in the emission request. |
| Fiscal cancellation/substitution numbering rules | Current MVP issuance flow does not model these lifecycle operations. |

---

## Assumptions & Open Questions

Every ambiguity is resolved or recorded here. These defaults are unconfirmed until the user approves this spec.

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --------------------- | -------------- | --------- | ---------- |
| Table name | `nfse_dps_sequence` | Matches existing `nfse_emission` naming and the entity purpose. | no |
| Logical sequence scope | One sequence per `(issuer_cnpj, dps_serial)` | DPS numbering must be independent by issuer and series; the provided attributes include both fields. | no |
| Database numeric types | PostgreSQL `NUMERIC(5,0)` for `dps_serial`, `NUMERIC(15,0)` for `last_dps_issued` | User specified `NUMBER(5)` and `NUMBER(15)`; PostgreSQL/Flyway equivalent is `NUMERIC`. | no |
| Initial sequence state | Missing row is created with `last_dps_issued = 0`, then first issued number is `1` | Allows automatic first use without a manual seed step. | no |
| `last_dps_issued` nullability | Store as `NOT NULL DEFAULT 0` even though only `issuer_cnpj` was explicitly marked `NOT NULL` | A nullable sequence counter complicates safe increments and has no useful meaning for generation. | no |
| DPS serial source | Use configured `nfse.emission.serie-dps`, converted to a numeric value | The current system already uses this property for DPS series. | no |
| Sequence consumption timing | Consume the number only for new idempotency keys, inside the same transaction that prepares and saves the emission | Existing idempotency behavior must remain the duplicate guard. | no |
| Local preparation failure | If XML build, validation, signing, or emission save fails, the sequence increment rolls back with the transaction | No DPS was persisted or sent, so the number can be retried. | no |
| Provider rejection/failure after signed XML is saved | The DPS number remains consumed | The signed DPS exists locally and may have been sent or attempted; reusing the number is unsafe. | no |
| Emission audit fields | Add generated `dps_serial` and `dps_number` to `nfse_emission` | Makes generated fiscal numbering searchable and testable without parsing XML. | no |

**Open questions:** none - all unresolved behavior is captured as assumptions above.

---

## User Stories

### P1: Persist DPS Sequence State - MVP

**User Story**: As the backend, I want a dedicated DPS sequence table so that NFS-e numbering is stored independently from emission requests.

**Why P1**: Without persistent sequence state, invoice generation cannot issue ordered DPS numbers.

**Acceptance Criteria**:

1. WHEN Flyway migrates the database THEN the system SHALL create `nfse_dps_sequence` with `id UUID PRIMARY KEY`, `issuer_cnpj VARCHAR(14) NOT NULL`, `dps_serial NUMERIC(5,0)`, and `last_dps_issued NUMERIC(15,0)`.
2. WHEN the sequence table is created THEN it SHALL enforce one sequence row per `(issuer_cnpj, dps_serial)`.
3. WHEN JPA validates the schema THEN the mapped sequence entity SHALL match the Flyway table and numeric precision.
4. WHEN a sequence row is created by the application THEN `issuer_cnpj` SHALL contain only the 14 CNPJ digits from the request.

**Independent Test**: Run Flyway/JPA validation against PostgreSQL and verify the sequence table, unique key, and entity mapping.

---

### P1: Allocate DPS Numbers for New Emissions - MVP

**User Story**: As the emission service, I want each new emission to receive the next DPS number for its issuer and series so that generated XML uses fiscal numbering.

**Why P1**: This is the behavior change that makes the table useful for invoice generation.

**Acceptance Criteria**:

1. WHEN a new emission request has no existing `idempotencyKey` THEN the system SHALL lock or create the sequence row for `(issuer_cnpj, dps_serial)`.
2. WHEN the sequence row has `last_dps_issued = N` THEN the new emission SHALL use `N + 1` as `nDPS`.
3. WHEN the new emission is prepared successfully THEN the system SHALL persist `last_dps_issued = N + 1`.
4. WHEN the DPS XML is generated THEN `<serie>` SHALL contain the configured DPS series without leading zeros and `<nDPS>` SHALL contain the allocated sequence number.
5. WHEN the DPS `Id` is generated THEN it SHALL include the allocated sequence number left-padded to 15 digits.

**Independent Test**: Create a sequence row with `last_dps_issued = 41`, emit a new request, and verify the XML contains `<nDPS>42</nDPS>` and the sequence row stores `42`.

---

### P1: Preserve Idempotency Without Sequence Gaps - MVP

**User Story**: As an API client retrying the same request, I want idempotent retries to return the existing emission without consuming new DPS numbers.

**Why P1**: The existing public API contract uses `idempotencyKey` as its duplicate guard.

**Acceptance Criteria**:

1. WHEN an emission request uses an `idempotencyKey` already stored in `nfse_emission` THEN the system SHALL return the existing emission response.
2. WHEN an idempotent hit occurs THEN the system SHALL NOT update `last_dps_issued`.
3. WHEN an idempotent hit occurs THEN the system SHALL NOT rebuild or resign the DPS XML.

**Independent Test**: Persist an emission and sequence state, repeat the same request, and verify the sequence counter is unchanged.

---

### P1: Prevent Duplicate Numbers Under Concurrency - MVP

**User Story**: As the backend under parallel issuance, I want sequence allocation to be serialized per issuer and series so that two emissions cannot receive the same `nDPS`.

**Why P1**: Duplicate fiscal numbers are a high-risk production failure.

**Acceptance Criteria**:

1. WHEN two new requests for the same `(issuer_cnpj, dps_serial)` run concurrently THEN each request SHALL receive a distinct DPS number.
2. WHEN concurrent requests target different issuers or different DPS series THEN their sequence counters SHALL be independent.
3. WHEN two transactions race to create the first sequence row THEN only one logical row SHALL exist and both emissions SHALL complete with distinct numbers or one transaction SHALL retry row acquisition before allocation.

**Independent Test**: Run concurrent allocation against the repository/service layer and assert unique, consecutive numbers for the same key.

---

## Edge Cases

- WHEN `prestador.cnpj` includes punctuation THEN the sequence key SHALL use only digits.
- WHEN the issuer document is not exactly 14 digits THEN the emission SHALL fail validation before sequence allocation.
- WHEN configured `serie-dps` cannot be represented as a numeric value up to 5 digits THEN the emission SHALL fail before sequence allocation.
- WHEN `last_dps_issued` is `999999999999999` THEN the next allocation SHALL fail with a controlled error instead of overflowing the 15-digit DPS limit.
- WHEN XML validation/signing fails inside preparation THEN sequence allocation SHALL roll back.

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| -------------- | ----- | ----- | ------ |
| DPSSEQ-01 | P1: Persist DPS Sequence State | Tasks T1, T2, T7 | Pending |
| DPSSEQ-02 | P1: Allocate DPS Numbers for New Emissions | Tasks T3, T4, T5, T6, T7 | Pending |
| DPSSEQ-03 | P1: Preserve Idempotency Without Sequence Gaps | Tasks T5, T6, T7 | Pending |
| DPSSEQ-04 | P1: Prevent Duplicate Numbers Under Concurrency | Tasks T2, T3, T7 | Pending |
| DPSSEQ-05 | Edge cases and input bounds | Tasks T1, T3, T4, T6, T7 | Pending |

**Coverage:** 5 total, 5 mapped to tasks, 0 unmapped.

---

## Success Criteria

- [ ] New emissions no longer derive `nDPS` from `idempotencyKey`.
- [ ] Repeated idempotent requests do not advance the DPS sequence.
- [ ] Concurrent emissions for the same issuer and series produce unique DPS numbers.
- [ ] Flyway migration plus JPA validation pass with the new sequence entity.
