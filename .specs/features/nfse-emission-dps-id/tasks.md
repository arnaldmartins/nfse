# NFS-e Emission DPS_ID Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name and follow its Execute flow and Critical Rules.** Do not search for skill files by filesystem path. The skill is the source of truth for the full flow (per-task cycle, sub-agent delegation, adequacy review, Verifier, discrimination sensor).

**If the skill cannot be activated, STOP and tell the user -- do not proceed without it.**

---

**Design**: `.specs/features/nfse-emission-dps-id/design.md`
**Status**: Draft

---

## Test Coverage Matrix

> Generated from codebase, project guidelines, and spec -- confirm before Execute. Guidelines found: `docs/DEVELOPMENT_REGIMENT.md`. Existing tests sampled: `DpsXmlBuilderTest`, `NfseEmissionRepositoryTest`, `NfseDpsSequenceMigrationTest`, `NfseEmissionServiceSequenceIntegrationTest`. Build commands inferred from `pom.xml`.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| ---------- | ------------------ | -------------------- | ---------------- | ----------- |
| Migration / Schema | integration | Assert column type `varchar(50)`, nullability `YES`, and index `idx_nfse_emission_dps_id` | `src/test/java/br/com/alvexustech/nfse/repository/*MigrationTest.java` | `mvn test` |
| JPA Entity | build + integration | JPA entity mapping validation and CRUD with `dpsId` populated and null | `src/test/java/br/com/alvexustech/nfse/repository/*Test.java` | `mvn test` |
| XML Builder / Record | unit | Assert `idWithoutPrefix()` strips `"DPS"` prefix from standard ID and handles edge cases (no prefix, null) | `src/test/java/br/com/alvexustech/nfse/service/DpsXmlBuilderTest.java` | `mvn test` |
| Service Orchestration | integration | Assert new emission sets `dpsId` without prefix in database, idempotency hit preserves it, and rollback cleans up | `src/test/java/br/com/alvexustech/nfse/service/NfseEmissionServiceSequenceIntegrationTest.java` | `mvn test` |

## Gate Check Commands

> Generated from codebase -- confirm before Execute.

| Gate Level | When to Use | Command |
| ---------- | ----------- | ------- |
| Quick | After tasks with unit tests only | `mvn test -Dtest=DpsXmlBuilderTest` |
| Full | After tasks with integration tests or service changes | `mvn test` |
| Build | After schema / migration / entity changes | `mvn test` |

---

## Execution Plan

Phases are ordered and run sequentially. The feature has 4 atomic tasks, so Execute runs inline in a single batch (≤ 8 tasks).

### Phase 1: Database Schema & Entity Mapping

```
T1 -> T2
```

### Phase 2: Prefix Stripping & Service Integration

```
T3 -> T4
```

---

## Task Breakdown

### T1: Add Flyway Migration for DPS_ID Column and Index

**What**: Create Flyway migration `V3__add_dps_id_to_nfse_emission.sql` adding `dps_id VARCHAR(50)` and index `idx_nfse_emission_dps_id` to `nfse_emission`.
**Where**: `src/main/resources/db/migration/V3__add_dps_id_to_nfse_emission.sql`, `src/test/java/br/com/alvexustech/nfse/repository/NfseDpsSequenceMigrationTest.java`
**Depends on**: None
**Reuses**: `V2__create_nfse_dps_sequence.sql`
**Requirement**: DPSID-01

**Tools**:

- MCP: NONE
- Skill: `tlc-spec-driven`

**Done when**:

- [x] Migration adds `dps_id VARCHAR(50)` (nullable) to `nfse_emission`.
- [x] Migration creates index `idx_nfse_emission_dps_id` on `nfse_emission (dps_id)`.
- [x] Migration test verifies `dps_id` metadata (`character varying`, nullable `YES`, max length 50) and index existence.
- [x] Gate check passes: `mvn test`.
- [x] Test count is reported with no silent deletions (18 passed).

**Tests**: integration
**Gate**: build

**Commit**: `feat(schema): add dps_id column and index to nfse_emission`

---

### T2: Map DPS_ID on NfseEmissionEntity

**What**: Add `dpsId` field, getter, and setter to `NfseEmissionEntity`, validating JPA schema mapping against PostgreSQL.
**Where**: `src/main/java/br/com/alvexustech/nfse/persistence/NfseEmissionEntity.java`, `src/test/java/br/com/alvexustech/nfse/repository/NfseEmissionRepositoryTest.java`
**Depends on**: T1
**Reuses**: Existing entity columns and repository tests
**Requirement**: DPSID-01

**Tools**:

- MCP: NONE
- Skill: `tlc-spec-driven`

**Done when**:

- [x] `NfseEmissionEntity` declares `@Column(name = "dps_id", length = 50) private String dpsId`.
- [x] Getter `getDpsId()` and setter `setDpsId(String dpsId)` are present.
- [x] Repository test verifies saving and retrieving an entity with `dpsId` populated as well as `null`.
- [x] Hibernate schema validation (`ddl-auto: validate`) passes cleanly against Flyway schema.
- [x] Gate check passes: `mvn test`.
- [x] Test count is reported with no silent deletions (18 passed).

**Tests**: integration
**Gate**: build

**Commit**: `feat(persistence): map dpsId field on NfseEmissionEntity`

---

### T3: Implement Prefix Stripping in DpsXml and Wire to Emission Preparation

**What**: Add `idWithoutPrefix()` helper to `DpsXmlBuilder.DpsXml` and populate `entity.setDpsId(dpsXml.idWithoutPrefix())` in `NfseEmissionService.prepareEmission`.
**Where**: `src/main/java/br/com/alvexustech/nfse/service/DpsXmlBuilder.java`, `src/main/java/br/com/alvexustech/nfse/service/NfseEmissionService.java`, `src/test/java/br/com/alvexustech/nfse/service/DpsXmlBuilderTest.java`
**Depends on**: T2
**Reuses**: Existing `DpsXmlBuilder` DOM generation and `NfseEmissionService.prepareEmission` transaction
**Requirement**: DPSID-02, DPSID-03

**Tools**:

- MCP: NONE
- Skill: `tlc-spec-driven`

**Done when**:

- [x] `DpsXml.idWithoutPrefix()` returns the ID without the leading `"DPS"` prefix (safe with null and strings without prefix).
- [x] Unit test in `DpsXmlBuilderTest` verifies standard 42-digit stripping, raw string preservation, and null handling.
- [x] `NfseEmissionService.prepareEmission()` calls `entity.setDpsId(dpsXml.idWithoutPrefix())` before persisting.
- [x] Gate check passes: `mvn test`.
- [x] Test count is reported with no silent deletions (19 passed).

**Tests**: unit + integration
**Gate**: full

**Commit**: `feat(emission): populate unprefixed dps_id during emission preparation`

---

### T4: Verify End-to-End Emission Integration, Idempotency, and Update Documentation

**What**: Add integration test asserting `dps_id` persistence on new emission, preservation on idempotent hit, rollback on failure, and update project knowledge base.
**Where**: `src/test/java/br/com/alvexustech/nfse/service/NfseEmissionServiceSequenceIntegrationTest.java`, `docs/PROJECT_KNOWLEDGE_BASE.md`
**Depends on**: T3
**Reuses**: Existing `NfseEmissionServiceSequenceIntegrationTest` setup and `docs/PROJECT_KNOWLEDGE_BASE.md` update format
**Requirement**: DPSID-02, DPSID-03

**Tools**:

- MCP: NONE
- Skill: `tlc-spec-driven`

**Done when**:

- [x] Integration test verifies `emission.getDpsId()` contains the exact 42-digit identifier without `"DPS"`.
- [x] Integration test verifies idempotent hit does not change or erase `dps_id`.
- [x] Integration test verifies preparation failure leaves no saved emission row with `dps_id`.
- [x] `docs/PROJECT_KNOWLEDGE_BASE.md` contains an update summary for the `dps_id` column addition.
- [x] Gate check passes: `mvn test`.
- [x] Test count is reported with no silent deletions (19 passed).

**Tests**: integration
**Gate**: full

**Commit**: `test(emission): verify dps_id persistence and update knowledge base`

---

## Phase Execution Map

```
Phase 1 -> Phase 2

Phase 1:  T1 -> T2
Phase 2:  T3 -> T4
```

---

## Task Granularity Check

| Task | Scope | Status |
| ---- | ----- | ------ |
| T1: Add Flyway migration for dps_id | 1 SQL migration + migration test update | ✅ Granular |
| T2: Map dpsId on NfseEmissionEntity | 1 JPA Entity modification + repository test | ✅ Granular |
| T3: Implement prefix stripping and wire to preparation | 1 helper method + service wire + unit test | ✅ Granular |
| T4: Verify emission integration and docs | Integration test assertions + knowledge base update | ✅ Granular |

---

## Diagram-Definition Cross-Check

| Task | Depends On (task body) | Diagram Shows | Status |
| ---- | ---------------------- | ------------- | ------ |
| T1 | None | Starts Phase 1 | ✅ Match |
| T2 | T1 | T1 -> T2 | ✅ Match |
| T3 | T2 | T2 -> T3 | ✅ Match |
| T4 | T3 | T3 -> T4 | ✅ Match |

---

## Test Co-location Validation

| Task | Code Layer Created/Modified | Matrix Requires | Task Says | Status |
| ---- | --------------------------- | --------------- | --------- | ------ |
| T1: Flyway migration | Migration / Schema | integration | integration | ✅ OK |
| T2: JPA Entity | JPA Entity | build + integration | integration | ✅ OK |
| T3: DpsXml & Emission Service | XML record / Service | unit + integration | unit + integration | ✅ OK |
| T4: Integration test & Docs | Service / Docs | integration | integration | ✅ OK |

---

## Tools Question Before Execute

Before execution, confirm tools to use:
- **Available MCPs**: NONE (local development)
- **Available Skills**: `tlc-spec-driven` (via repo-local skill definitions)
- **Test / Build Runner**: Maven (`mvn test`)
