# NFS-e DPS Sequence Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name and follow its Execute flow and Critical Rules.** Do not search for skill files by filesystem path. The skill is the source of truth for the full flow (per-task cycle, sub-agent delegation, adequacy review, Verifier, discrimination sensor).

**If the skill cannot be activated, STOP and tell the user -- do not proceed without it.**

---

**Design**: `.specs/features/nfse-dps-sequence/design.md`
**Status**: Draft

---

## Test Coverage Matrix

> Generated from codebase, project guidelines, and spec -- confirm before Execute. Guidelines found: none -- strong defaults applied. Existing tests sampled: `src/test/java/br/com/alvexustech/nfse/service/DpsXmlBuilderTest.java`. Build commands inferred from `pom.xml`.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| ---------- | ------------------ | -------------------- | ---------------- | ----------- |
| XML builder / formatting logic | unit | All branches touched by DPS serial/number inputs; 1:1 to DPSSEQ-02 and DPSSEQ-05 XML outcomes | `src/test/java/br/com/alvexustech/nfse/service/*Test.java` | `mvn test` |
| Sequence service / business logic | unit + integration | All allocation branches, idempotency interaction, bounds, first row creation, and concurrent same-key allocation | `src/test/java/br/com/alvexustech/nfse/service/*Test.java` | `mvn test` |
| Repository / data access | integration | Key query paths, pessimistic lock behavior, unique `(issuer_cnpj, dps_serial)` constraint | `src/test/java/br/com/alvexustech/nfse/repository/*Test.java` | `mvn test` |
| Entity / Flyway schema | build + integration | Hibernate schema validation and Flyway migration apply cleanly | `src/test/java/br/com/alvexustech/nfse/**` | `mvn test` |

## Gate Check Commands

> Generated from codebase -- confirm before Execute.

| Gate Level | When to Use | Command |
| ---------- | ----------- | ------- |
| Quick | After tasks with unit tests only | `mvn test` |
| Full | After tasks with repository/integration/concurrency coverage | `mvn test` |
| Build | After schema/entity/config changes | `mvn test` |

---

## Execution Plan

Phases are ordered and run sequentially. The feature has 7 tasks, so Execute should run inline in one batch unless the task list grows.

### Phase 1: Schema and Persistence

```
T1 -> T2
```

### Phase 2: Allocation Logic

```
T3 -> T4
```

### Phase 3: Emission Integration

```
T5 -> T6 -> T7
```

---

## Task Breakdown

### T1: Add DPS Sequence Schema Migration

**What**: Create Flyway migration for `nfse_dps_sequence` and emission audit columns.
**Where**: `src/main/resources/db/migration/V2__create_nfse_dps_sequence.sql`
**Depends on**: None
**Reuses**: `src/main/resources/db/migration/V1__create_nfse_emission.sql`
**Requirement**: DPSSEQ-01, DPSSEQ-05

**Tools**:

- MCP: NONE
- Skill: `tlc-spec-driven`

**Done when**:

- [x] Migration creates `nfse_dps_sequence` with requested columns and PostgreSQL numeric equivalents.
- [x] Migration enforces unique `(issuer_cnpj, dps_serial)`.
- [x] Migration adds `dps_serial` and `dps_number` audit columns to `nfse_emission`.
- [x] Gate check passes: `mvn test`.
- [x] Test count is reported with no silent deletions.

**Tests**: integration
**Gate**: build

**Commit**: `feat(sequence): add dps sequence schema`

---

### T2: Add Sequence Entity and Repository

**What**: Create JPA mapping and repository for sequence rows, including locked lookup by issuer and series.
**Where**: `src/main/java/br/com/alvexustech/nfse/persistence/NfseDpsSequenceEntity.java`, `src/main/java/br/com/alvexustech/nfse/repository/NfseDpsSequenceRepository.java`
**Depends on**: T1
**Reuses**: `NfseEmissionEntity`, `NfseEmissionRepository`
**Requirement**: DPSSEQ-01, DPSSEQ-04

**Tools**:

- MCP: NONE
- Skill: `tlc-spec-driven`

**Done when**:

- [x] Entity maps `id`, `issuerCnpj`, `dpsSerial`, and `lastDpsIssued` with correct nullability and precision.
- [x] Entity maintains `createdAt` and `updatedAt` consistently with existing entity style.
- [x] Repository exposes normal and pessimistic-write lookup by `(issuer_cnpj, dps_serial)`.
- [x] Repository integration test verifies unique key and locked lookup query compile/run.
- [x] Gate check passes: `mvn test`.
- [x] Test count is reported with no silent deletions.

**Tests**: integration
**Gate**: full

**Commit**: `feat(sequence): map dps sequence entity`

---

### T3: Implement Sequence Allocation Service

**What**: Add service that validates inputs, creates or locks the sequence row, increments safely, and returns allocated DPS serial/number.
**Where**: `src/main/java/br/com/alvexustech/nfse/service/NfseDpsSequenceService.java`
**Depends on**: T2
**Reuses**: digit normalization and padding rules from `DpsXmlBuilder`
**Requirement**: DPSSEQ-02, DPSSEQ-04, DPSSEQ-05

**Tools**:

- MCP: NONE
- Skill: `tlc-spec-driven`

**Done when**:

- [x] `allocateNext` normalizes issuer CNPJ to exactly 14 digits.
- [x] `allocateNext` parses configured DPS series as a numeric value up to 5 digits.
- [x] Missing sequence row starts at zero and returns number one.
- [x] Existing row with `N` returns `N + 1` and persists `N + 1`.
- [x] Upper bound at 15 digits fails before overflow.
- [x] Race on first row creation is handled by retrying row acquisition or surfacing a controlled failure covered by tests.
- [x] Unit/integration tests cover all outcomes above.
- [x] Gate check passes: `mvn test`.
- [x] Test count is reported with no silent deletions.

**Tests**: unit + integration
**Gate**: full

**Commit**: `feat(sequence): allocate dps numbers`

---

### T4: Change DPS XML Builder to Use Allocated Number

**What**: Replace idempotency-derived DPS number generation with caller-provided `dpsSerial` and `dpsNumber`.
**Where**: `src/main/java/br/com/alvexustech/nfse/service/DpsXmlBuilder.java`, `src/test/java/br/com/alvexustech/nfse/service/DpsXmlBuilderTest.java`
**Depends on**: T3
**Reuses**: existing XML creation and schema validation tests
**Requirement**: DPSSEQ-02, DPSSEQ-05

**Tools**:

- MCP: NONE
- Skill: `tlc-spec-driven`

**Done when**:

- [x] Builder API accepts allocated serial and number.
- [x] `<serie>` uses the numeric serial without leading zeros.
- [x] `<nDPS>` uses the allocated number.
- [x] DPS `Id` contains the allocated number left-padded to 15 digits.
- [x] Unit tests verify XML contents and schema validity.
- [x] Gate check passes: `mvn test`.
- [x] Test count is reported with no silent deletions.

**Tests**: unit
**Gate**: quick

**Commit**: `feat(sequence): use allocated dps number in xml`

---

### T5: Persist Emission DPS Audit Fields

**What**: Map and populate `dpsSerial` and `dpsNumber` on `NfseEmissionEntity`.
**Where**: `src/main/java/br/com/alvexustech/nfse/persistence/NfseEmissionEntity.java`
**Depends on**: T4
**Reuses**: existing emission entity constructor/getters style
**Requirement**: DPSSEQ-02, DPSSEQ-03

**Tools**:

- MCP: NONE
- Skill: `tlc-spec-driven`

**Done when**:

- [ ] Entity maps audit columns with numeric precision matching Flyway.
- [ ] New emissions can store allocated serial and number.
- [ ] Existing idempotent emissions can be read with nullable audit fields for pre-feature rows.
- [ ] Gate check passes: `mvn test`.
- [ ] Test count is reported with no silent deletions.

**Tests**: build + integration
**Gate**: build

**Commit**: `feat(sequence): store dps number on emissions`

---

### T6: Integrate Sequence Allocation into Emission Preparation

**What**: Inject the allocation service and use it only after idempotency miss and before DPS XML build.
**Where**: `src/main/java/br/com/alvexustech/nfse/service/NfseEmissionService.java`
**Depends on**: T5
**Reuses**: existing `TransactionTemplate` boundary and idempotency lookup
**Requirement**: DPSSEQ-02, DPSSEQ-03, DPSSEQ-05

**Tools**:

- MCP: NONE
- Skill: `tlc-spec-driven`

**Done when**:

- [ ] Existing `idempotencyKey` hit returns before sequence allocation.
- [ ] New request allocates exactly one DPS number inside the preparation transaction.
- [ ] Prepared emission stores signed XML plus `dpsSerial` and `dpsNumber`.
- [ ] Local preparation failures roll back the sequence increment.
- [ ] Service tests cover new request, idempotent hit, and preparation failure rollback.
- [ ] Gate check passes: `mvn test`.
- [ ] Test count is reported with no silent deletions.

**Tests**: unit + integration
**Gate**: full

**Commit**: `feat(sequence): allocate sequence during emission`

---

### T7: Add Concurrency Coverage and Documentation Update

**What**: Verify concurrent allocation behavior and update the project knowledge base.
**Where**: `src/test/java/br/com/alvexustech/nfse/service/NfseDpsSequenceServiceTest.java`, `docs/PROJECT_KNOWLEDGE_BASE.md`
**Depends on**: T6
**Reuses**: project knowledge base persistence/runtime-flow sections
**Requirement**: DPSSEQ-01, DPSSEQ-02, DPSSEQ-03, DPSSEQ-04, DPSSEQ-05

**Tools**:

- MCP: NONE
- Skill: `tlc-spec-driven`

**Done when**:

- [ ] Concurrent same issuer/series allocations produce distinct numbers.
- [ ] Different issuer or serial allocations maintain independent counters.
- [ ] Documentation describes the sequence table, allocation flow, and idempotency interaction.
- [ ] Gate check passes: `mvn test`.
- [ ] Test count is reported with no silent deletions.

**Tests**: integration
**Gate**: full

**Commit**: `test(sequence): verify dps allocation concurrency`

---

## Phase Execution Map

```
Phase 1 -> Phase 2 -> Phase 3

Phase 1:  T1 -> T2
Phase 2:  T3 -> T4
Phase 3:  T5 -> T6 -> T7
```

---

## Diagram-Definition Cross-Check

| Task | Depends On (task body) | Diagram Shows | Status |
| ---- | ---------------------- | ------------- | ------ |
| T1 | None | Starts Phase 1 | OK |
| T2 | T1 | T1 -> T2 | OK |
| T3 | T2 | T2 -> T3 | OK |
| T4 | T3 | T3 -> T4 | OK |
| T5 | T4 | T4 -> T5 | OK |
| T6 | T5 | T5 -> T6 | OK |
| T7 | T6 | T6 -> T7 | OK |

## Test Co-location Validation

| Task | Code Layer Created/Modified | Matrix Requires | Task Says | Status |
| ---- | --------------------------- | --------------- | --------- | ------ |
| T1 | Flyway schema | build + integration | integration | OK |
| T2 | Entity / repository | integration | integration | OK |
| T3 | Sequence service | unit + integration | unit + integration | OK |
| T4 | XML builder | unit | unit | OK |
| T5 | Emission entity | build + integration | build + integration | OK |
| T6 | Emission service | unit + integration | unit + integration | OK |
| T7 | Sequence service / docs | integration | integration | OK |

## Tools Question Before Execute

Before implementation, confirm whether each task should use only local filesystem/Maven tools or any external MCPs. No project MCPs were discoverable in this session; available skills include `tlc-spec-driven` through the repo-local skill files.
