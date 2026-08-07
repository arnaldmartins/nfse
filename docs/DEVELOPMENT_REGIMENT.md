# Development Regiment

This document guides humans and AI agents evolving this NFS-e MVP with SOLID,
DRY, KISS, YAGNI, TDD/BDD, and Hexagonal Architecture.

The goal is practical learning through real feature work. Do not turn this MVP
into a framework exercise. Apply each principle gradually when it reduces risk,
clarifies behavior, or makes the next change easier.

## Current Baseline

The project is a Java 17 / Spring Boot 3 API for Brazilian NFS-e issuance using
the national standard. It already has useful boundaries:

- `dto`: HTTP/provider request and response records.
- `service`: current use-case orchestration, DPS XML generation, and XML
  validation.
- `provider` and `client`: national provider abstraction and HTTP adapter.
- `signature`, `certificate`, `persistence`, and `repository`: infrastructure
  concerns.
- `domain`: stable business concepts, currently status and provider code.

The main architecture pressure points are:

- `NfseEmissionService` currently mixes use-case orchestration, persistence
  transaction handling, provider calls, and provider status mapping.
- `DpsXmlBuilder` is intentionally manual MVP XML generation.
- Test coverage is concentrated on DPS XML schema validation.
- The current package layout is pragmatic Spring layering, not yet full
  hexagonal architecture.

Treat these as learning and refactoring opportunities when related feature work
touches them.

## Operating Rules

1. Start from behavior, not structure.
   Define what the invoice flow must do before moving classes around.

2. Prefer the smallest useful change.
   A feature should leave the design a little clearer, not introduce a generic
   architecture for imagined future providers, taxes, queues, or workflows.

3. Make fiscal behavior explicit.
   Any change to XML, provider payloads, status mapping, idempotency,
   persistence, or errors must have a test or executable example describing the
   rule.

4. Keep the knowledge base current.
   Update `docs/PROJECT_KNOWLEDGE_BASE.md` when runtime behavior,
   configuration, provider integration, database schema, or fiscal assumptions
   change.

5. Separate learning refactors from feature changes when possible.
   Small refactors that directly enable a feature are welcome. Broad package
   reshapes should be independent and covered by characterization tests first.

## Feature Workflow

Use this loop for every new feature or fix:

1. Requirement
   Write the behavior in business language:
   "Given an emission request with X, when Y happens, then Z is persisted or
   returned."

2. Example
   Add or update one executable example first:
   unit test, integration test, WebFlux controller test, repository test,
   schema validation test, or HTTP example in `docs/http`.

3. Design check
   Decide where the behavior belongs:
   domain rule, application use case, outbound port, inbound adapter,
   persistence adapter, provider adapter, or configuration.

4. Implementation
   Write the simplest code that satisfies the example and keeps existing tests
   green.

5. Refactor
   Remove duplication, improve names, and move logic toward the correct
   boundary only where the feature has proven the need.

6. Documentation
   Update this regiment only for process changes. Update the knowledge base for
   actual system behavior.

## TDD And BDD Practice

Use TDD for low-level behavior and BDD language for business flows.

Recommended test order:

- Domain or mapper rules: plain JUnit tests without Spring.
- XML changes: builder/mapper tests that validate against the bundled XSD.
- Provider behavior: tests around provider/client mapping, ideally with mocked
  HTTP exchange.
- Use-case orchestration: service tests with fake ports or mocked adapters.
- API behavior: WebFlux/controller tests for validation and response shape.
- Persistence behavior: repository or migration checks when schema changes.

Prefer test names that read like requirements:

```java
@Test
void returnsExistingEmissionWhenIdempotencyKeyWasAlreadyUsed() {
}

@Test
void mapsNationalProviderAccessKeyToAuthorizedEmission() {
}
```

For BDD scenarios, keep them near the work item, issue, or test class:

```text
Given a valid DPS request for a CPF tomador
When the emission is prepared
Then the unsigned DPS XML is valid against the national schema
And the local emission status becomes SIGNED
```

Do not add Cucumber or a BDD framework until plain tests become insufficient.

## Hexagonal Architecture Direction

Use hexagonal architecture as a compass, not as a mandatory rewrite.

Target dependency direction:

```text
Inbound adapters -> application use cases -> domain
Outbound adapters -> application ports <- application use cases
Infrastructure depends inward; domain does not depend on Spring, JPA, WebClient,
XML libraries, certificates, or provider DTOs.
```

Current package names can evolve gradually:

- `domain`: fiscal concepts, statuses, value objects, business decisions.
- `application`: use cases and ports when `service` becomes too mixed.
- `adapter/in/web`: controllers and API DTO mapping.
- `adapter/out/provider`: national and future provider adapters.
- `adapter/out/persistence`: JPA entities and repositories.
- `adapter/out/xml`: DPS XML generation, validation, and signature adapters.
- `config`: Spring wiring.

Move code only when there is a concrete reason:

- A class is hard to test because Spring/infrastructure is mixed with a rule.
- The same behavior is duplicated in two adapters.
- A fiscal rule is hidden inside provider/client/persistence code.
- A future provider would require editing unrelated application logic.

## SOLID In This Project

### Single Responsibility

Classes should have one reason to change.

Apply when:

- `NfseEmissionService` grows new responsibilities.
- Provider status mapping gains more cases.
- XML generation accumulates fiscal calculations.

Practical move:

- Extract a focused collaborator such as `ProviderStatusMapper`,
  `EmissionPreparationUseCase`, or `DpsNumberGenerator` only when tests show the
  current class is doing too much.

### Open/Closed

New provider behavior should be added through new implementations, not large
conditionals.

Current example:

- `NfseProvider` and `NfseProviderRegistry` already support this direction.

Practical move:

- Add a new provider as another adapter implementing the provider port.
- Avoid changing application flow just to support provider-specific HTTP shape.

### Liskov Substitution

Anything implementing a port must respect the same contract.

Practical move:

- Document what `emit`, `consultStatus`, and `consultDps` promise about errors,
  empty responses, and status fields before adding another provider.

### Interface Segregation

Ports should expose only what a use case needs.

Apply when:

- Some providers cannot support both `consultStatus` and `consultDps`.
- A use case needs only emission, not status consultation.

Practical move:

- Split provider capabilities only after the mismatch is real.

### Dependency Inversion

Application rules should depend on ports, not infrastructure details.

Apply when:

- Testing a use case requires JPA, WebClient, certificates, or real XML signing.

Practical move:

- Introduce application ports such as `EmissionRepositoryPort`,
  `NfseProviderPort`, `XmlSignerPort`, or `DpsXmlPort` and adapt existing
  Spring components behind them.

## DRY, KISS, And YAGNI

### DRY

Remove duplication of knowledge, not every repeated line.

Duplicate fiscal rules are dangerous:

- document normalization.
- DPS number/id construction.
- provider status mapping.
- service/tax code normalization.
- idempotency behavior.

Repeated simple test setup is acceptable until it becomes noisy. Prefer test
fixtures/builders only after the third meaningful duplication.

### KISS

Prefer direct code while the domain is still small.

Good MVP choices:

- Plain JUnit tests before Cucumber.
- Simple ports before a command bus.
- Transaction boundaries visible in the use case before generic unit-of-work
  abstractions.
- Explicit provider mapping before reflection or generic mappers.

### YAGNI

Do not build future features until they are requested by a real scenario.

Avoid adding prematurely:

- multi-tenant abstractions.
- generic tax engines.
- async queues/outbox.
- provider plugin systems.
- generated XML models for every NFS-e document.
- full DDD aggregate hierarchies.

Exception: small interfaces or seams are acceptable when they directly make a
current feature testable.

## Refactoring Triggers

Refactor only when at least one trigger is present:

- A failing or pending test is hard to write because logic is coupled to
  infrastructure.
- A class needs a third unrelated responsibility.
- A second provider or XML format duplicates a rule.
- A bug fix requires understanding too many unrelated concepts.
- A fiscal rule is not named anywhere in code.
- A persistence change risks breaking idempotency or audit behavior.

Preferred refactoring sequence:

1. Add characterization tests for current behavior.
2. Extract pure logic first.
3. Introduce a port only if an adapter boundary is needed.
4. Move packages after behavior is protected.
5. Keep public API behavior stable unless the feature explicitly changes it.

## Quality Checklist

Before finishing a change, verify:

- Requirement is captured as at least one test or executable example.
- `mvn test` passes, or the reason it cannot run is documented.
- Fiscal XML changes validate against XSD.
- Provider status/error mapping is covered for new cases.
- Idempotency behavior is preserved.
- New database changes use a new Flyway migration.
- Secrets and certificate material are not committed.
- `docs/PROJECT_KNOWLEDGE_BASE.md` is updated when behavior changes.
- The implementation is no more generic than the current requirement needs.

## Guidance For AI Agents

When working in this repository:

- Read `README.md`, `docs/PROJECT_KNOWLEDGE_BASE.md`, and this file before
  larger changes.
- Inspect existing code before proposing architecture changes.
- Prefer small patches with tests over large rewrites.
- Do not rename packages into hexagonal architecture unless the task requires
  it or tests expose a clear need.
- Do not edit existing Flyway migrations; add a new one.
- Do not invent fiscal rules. If official NFS-e behavior is uncertain, ask the
  human to confirm or verify against official documentation before coding.
- When touching XML generation, add schema validation coverage.
- When touching provider calls, protect idempotency and error persistence.
- When touching error handling, check HTTP status mapping in
  `GlobalExceptionHandler`.

## Practical Learning Path

Use real work to introduce concepts in this order:

1. Add missing tests around current behavior.
2. Extract pure mappers/rules from `NfseEmissionService` or `DpsXmlBuilder`.
3. Introduce application ports only where tests need infrastructure isolation.
4. Move one use case at a time toward `application` and adapters.
5. Revisit package structure after several features prove the target shape.

This keeps the project useful as an MVP while making each new feature a concrete
exercise in design, testing, and fiscal correctness.
