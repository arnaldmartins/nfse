# NFS-e DPS Sequence Validation

**Date**: 2026-09-23
**Spec**: `.specs/features/nfse-dps-sequence/spec.md`
**Diff range**: `8791787..3893a64`
**Verifier**: independent sub-agent (author != verifier)

---

## Task Completion

| Task | Status | Notes |
| ---- | ------ | ----- |
| T1 | ✅ Done | Migration is present and migration assertions pass. |
| T2 | ✅ Done | Entity and pessimistic-lock repository query are present and exercised. |
| T3 | ⚠️ Partial | First-row collision reports an exception rather than retrying locked acquisition. |
| T4 | ✅ Done | Allocated series/number are used in XML and DPS ID. |
| T5 | ✅ Done | Emission audit fields are mapped and persisted. |
| T6 | ✅ Done | Allocation follows idempotency lookup and rolls back on local preparation failure. |
| T7 | ⚠️ Partial | Existing-row concurrency and independent keys are covered; the empty-key creation race is not. |

## Spec-Anchored Acceptance Criteria

| Criterion (WHEN X THEN Y) | Spec-defined outcome | `file:line` + assertion | Result |
| --- | --- | --- | --- |
| P1 persistence: Flyway migration creates the requested table and columns | UUID id; 14-char issuer; `NUMERIC(5,0)` serial; `NUMERIC(15,0)` counter | `NfseDpsSequenceMigrationTest.java:37` - `containsExactly("uuid", "NO", null)`; `:38-40` assert issuer and both numeric precisions | ✅ PASS |
| P1 persistence: one row per issuer and serial | Unique `(issuer_cnpj, dps_serial)` | `NfseDpsSequenceMigrationTest.java:42-43` asserts constraint name and columns; `NfseDpsSequenceRepositoryTest.java:50-52` asserts duplicate insert fails | ✅ PASS |
| P1 persistence: JPA mapping matches Flyway precision | Hibernate validation accepts matching entity/table mapping | `application.yml:8-10` enables `ddl-auto: validate`; `NfseDpsSequenceRepositoryTest.java:18-20` starts JPA against Flyway PostgreSQL schema; gate passed | ✅ PASS |
| P1 persistence: application-created issuer key is 14 digits | Punctuation is removed before sequence row persistence | `NfseDpsSequenceServiceIntegrationTest.java:38-45` asserts stored `"66375620000113"` | ✅ PASS |
| P1 allocation: new idempotency miss locks or creates sequence row | Row is locked when present or created on first allocation | `NfseDpsSequenceServiceIntegrationTest.java:38-45` asserts first row; `:52-58` asserts existing row allocation | ✅ PASS |
| P1 allocation: existing N allocates N+1 | `41` becomes allocated/persisted `42` | `NfseDpsSequenceServiceIntegrationTest.java:56-58` - `isEqualTo(2)`, `isEqualTo(42L)`, `isEqualTo(42L)` | ✅ PASS |
| P1 allocation: successful preparation persists N+1 | Emission and sequence store `42` | `NfseEmissionServiceSequenceIntegrationTest.java:97-101` asserts audit values, counter, and builder call | ✅ PASS |
| P1 XML: series has no leading zeroes and nDPS is allocated value | `<serie>7</serie>`, `<nDPS>42</nDPS>` | `DpsXmlBuilderTest.java:71-72` asserts both exact XML fragments | ✅ PASS |
| P1 XML ID contains a 15-digit padded allocated number | DPS ID ends in serial plus `000000000000042` | `DpsXmlBuilderTest.java:73` - `endsWith("00007000000000000042")` | ✅ PASS |
| P1 idempotency: stored key returns existing response | Existing emission is returned without preparation/provider work | `NfseEmissionServiceSequenceIntegrationTest.java:111-116` asserts execution completes, counter stays `42`, and mocks receive no interactions | ✅ PASS |
| P1 idempotency: hit does not update counter | Counter remains `42` | `NfseEmissionServiceSequenceIntegrationTest.java:113-115` - `isEqualTo(42L)` | ✅ PASS |
| P1 idempotency: hit does not rebuild or resign XML | XML builder and signer are not called | `NfseEmissionServiceSequenceIntegrationTest.java:116` - `verifyNoInteractions(dpsXmlBuilder, xmlSignatureService, ...)` | ✅ PASS |
| P1 concurrency: same existing key receives distinct numbers | Concurrent allocations receive `1` and `2`; stored counter is `2` | `NfseDpsSequenceServiceIntegrationTest.java:100-103` - `containsExactlyInAnyOrder(1L, 2L)` and `isEqualTo(2L)` | ✅ PASS |
| P1 concurrency: different issuer or serial keys are independent | All three independent keys begin at `1` | `NfseDpsSequenceServiceIntegrationTest.java:124-131` asserts each allocation/counter is `1` | ✅ PASS |
| P1 concurrency: two transactions racing to create first row complete distinctly or retry acquisition | One logical row and two distinct allocations; alternatively retry locked lookup | No evidence. `NfseDpsSequenceService.java:41-46` converts the unique-key race to `IllegalStateException` instead of retrying; only the controlled exception path is tested at `NfseDpsSequenceServiceTest.java:25-35`. | ❌ GAP |

**Status**: ❌ 14/15 acceptance criteria have exact-outcome evidence; no spec-precision gaps.

## Edge Cases

- [x] Punctuated CNPJ normalizes to 14 digits: `NfseDpsSequenceServiceIntegrationTest.java:38-45`.
- [x] Invalid issuer fails before allocation: `NfseDpsSequenceServiceIntegrationTest.java:75-81`.
- [x] Non-numeric/over-five-digit series fails before allocation: `NfseDpsSequenceServiceIntegrationTest.java:78-81`.
- [x] Fifteen-digit maximum fails without counter mutation: `NfseDpsSequenceServiceIntegrationTest.java:62-70`.
- [x] Local preparation failure rolls back sequence and emission: `NfseEmissionServiceSequenceIntegrationTest.java:120-133`.

## Discrimination Sensor

All mutations ran only in `/tmp/nfse-dps-verifier.HwlUaE`, created from `git archive 3893a64`; the scratch tree was deleted after each result was captured.

| Mutation | File:line | Description | Killed? |
| --- | --- | --- | --- |
| 1 | `NfseDpsSequenceService.java:36` | Replaced `lastDpsIssued + 1L` with `lastDpsIssued` | ✅ Killed by `NfseDpsSequenceServiceIntegrationTest`: 4 failures, including exact `42`, first-use, and concurrent values |
| 2 | `DpsXmlBuilder.java:47` | Rendered `dpsNumber + 1` into XML/ID | ✅ Killed by `DpsXmlBuilderTest`: exact `<nDPS>42</nDPS>` assertion failed |
| 3 | `NfseEmissionService.java:101` | Bypassed stored-idempotency early return | ✅ Killed by `NfseEmissionServiceSequenceIntegrationTest`: idempotent request failed during unintended preparation |

**Sensor depth**: lightweight
**Result**: 3/3 killed - PASS ✅

## Code Quality

| Principle | Status |
| --- | --- |
| No features beyond what was asked | ✅ |
| No single-use abstraction or unnecessary flexibility | ✅ |
| Diff is scoped to sequence storage, allocation, XML, audit, tests, and documentation | ✅ |
| Matches existing Spring Data/JPA and transaction patterns | ✅ |
| Tests map to ACs, listed edge cases, or task criteria | ✅ |
| Spec-anchored outcome values are exact where specified | ✅ except first-row concurrency gap |
| Per-layer expectation met | ❌ First-row creation race lacks required integration coverage and behavior |
| Documented guidelines followed | ✅ `coding-principles.md`; no project-specific testing guideline found |

## Gate Check

- **Gate command**: `mvn test`
- **Result**: 17 passed, 0 failed, 0 skipped
- **Test count before feature**: 1
- **Test count after feature**: 17
- **Delta**: +16 tests
- **Skipped tests**: none
- **Failures**: none

## Fix Plans

### Fix 1: Retry first-row sequence acquisition after a unique-key race

- **Root cause**: `createFirstSequenceRow` throws a controlled exception on `DataIntegrityViolationException` (`NfseDpsSequenceService.java:41-46`) instead of re-reading the now-created row under pessimistic lock.
- **Fix task**: After the insert collision, retry `findByIssuerCnpjAndDpsSerialForUpdate`, allocate from that row in the same request flow, and add a two-thread empty-key integration test that asserts one row and allocations `1`/`2`.
- **Priority**: Major

## Requirement Traceability Update

| Requirement | Previous Status | Validation status |
| --- | --- | --- |
| DPSSEQ-01 | Pending | ✅ Verified |
| DPSSEQ-02 | Pending | ✅ Verified |
| DPSSEQ-03 | Pending | ✅ Verified |
| DPSSEQ-04 | Pending | ❌ Needs Fix - first-row race |
| DPSSEQ-05 | Pending | ✅ Verified |

## Summary

**Overall**: ❌ Not Ready

**Spec-anchored check**: 14/15 ACs matched spec outcome; 0 spec-precision gaps.
**Sensor**: 3/3 mutations killed.
**Gate**: 17 passed, 0 failed, 0 skipped.

**What works**: persistence and schema validation, allocated XML/audit values, idempotency, input bounds, rollback, existing-row serialization, and counter independence.

**Issues found**: the required concurrent first-row creation recovery is absent; a duplicate-row race fails one request rather than allowing both to complete or retrying acquisition.

**Next steps**: implement the retry and its empty-key concurrency integration test, then re-run independent validation.
