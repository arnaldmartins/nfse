# NFS-e Nacional Backend

## What This Project Does

This project is a Java 17 / Spring Boot 3 backend for automated Brazilian
NFS-e issuance using the national NFS-e standard. Its current scope is an MVP
for receiving an internal invoice issuance request, generating a DPS XML,
validating it against the bundled national XSD, signing it with an A1
certificate, sending it to the national provider through mTLS, and persisting
the provider state.

The public API is intentionally small:

- `POST /api/v1/nfse/emissoes`: starts an NFS-e emission request.
- `GET /api/v1/nfse/emissoes/{id}/status`: checks the persisted emission and,
  when a provider protocol exists, asks the configured provider for the latest
  status.

The codebase is not yet a complete production fiscal platform. It has the
important structural pieces for the national provider, but several fiscal,
operational, and asynchronous processing concerns remain intentionally simple.

## Technology Stack

- Java 17.
- Spring Boot 3.3.6.
- Spring WebFlux and Reactor for HTTP API/provider calls.
- Spring Data JPA for persistence.
- PostgreSQL as the database.
- Flyway for schema migrations.
- Apache Santuario XML Security for XMLDSig signatures.
- Reactor Netty `WebClient` with mTLS for national provider calls.
- Logback, with text logs locally and JSON logs under the `prod` Spring profile.
- JUnit 5 / Spring Boot Test / AssertJ for tests.

## Repository Layout

- `src/main/java/br/com/alvexustech/nfse/NfseApplication.java`: Spring Boot
  entry point and configuration properties registration.
- `service`: HTTP controller, emission orchestration, DPS XML generation, and
  XSD validation.
- `provider`: provider abstraction and national provider implementation.
- `client`: HTTP client for the national NFS-e API.
- `certificate`: A1 `.pfx` loading and Netty SSL/mTLS setup.
- `signature`: XMLDSig signing implementation.
- `persistence` and `repository`: JPA entity and database access.
- `dto`: request/response records for API and national provider payloads.
- `domain`: stable enums, currently emission status and provider code.
- `exception`: standardized API error handling and custom runtime exceptions.
- `logging`: correlation-id WebFlux filter.
- `util`: small helpers, currently gzip/base64 compression.
- `src/main/resources/db/migration`: Flyway migrations.
- `src/main/resources/xsd/nfse-v1.01`: bundled national NFS-e XSD files.
- `docs/http`: runnable HTTP examples and sample responses.
- `compose.yml`: local PostgreSQL service.

## Main Runtime Flow

The emission flow is coordinated by `NfseEmissionService`.

1. `NfseEmissionController.emit` receives `EmitirNfseRequest`.
2. Bean validation checks required fields on the DTO records.
3. `NfseEmissionService.emit` starts a blocking database section on
   `Schedulers.boundedElastic()`.
4. `prepareEmission` checks `idempotencyKey` in `NfseEmissionRepository`.
5. If the key already exists, the persisted entity is returned and the provider
   is not called again.
6. If it is a new request, a `NfseEmissionEntity` is created with status
   `RECEIVED`.
7. `DpsXmlBuilder` builds the DPS XML using request data plus configured
   municipality and emission defaults.
8. `NfseXmlValidator` validates the unsigned DPS XML against the configured
   XSD, defaulting to `xsd/nfse-v1.01/DPS_v1.01.xsd`.
9. `SantuarioXmlSignatureService` signs the XML using the loaded A1 certificate
   and the DPS `Id` as the XML reference.
10. The signed XML is validated again against the DPS XSD.
11. The entity stores both unsigned and signed XML and moves to `SIGNED`.
12. The selected provider is resolved through `NfseProviderRegistry`.
13. `NacionalNfseProvider` compresses the signed XML with GZip, base64-encodes
   it, and sends it as `dpsXmlGZipB64`.
14. `NacionalNfseClient` posts to the configured national API endpoint using
   `WebClient` with mTLS.
15. Provider response fields are mapped back to local status, protocol,
   response payload, and access key when authorized.
16. Any provider error marks an existing idempotent record as `FAILED` and
   returns a generic internal API error.

Status consultation follows a separate path:

1. `GET /api/v1/nfse/emissoes/{id}/status` loads the entity by UUID.
2. If no provider protocol exists, the service returns the current local status.
3. If a protocol exists, it calls `provider.consultStatus(protocolo)`.
4. The response message and mapped status are persisted.
5. The current status, protocol, access key, and provider message are returned.

## API Model

### Emission Request

`EmitirNfseRequest` contains:

- `idempotencyKey`: required unique business key. This is the primary guard
  against duplicate provider submissions.
- `competencia`: required service competence date.
- `prestador`: required issuer data.
- `tomador`: required service taker data.
- `servico`: required service code, description, amount, and optional municipal
  taxation code.

Current DTO limitations:

- `PrestadorDto` field name is `cnpj`, but `DpsXmlBuilder` accepts either CPF
  or CNPJ length when building XML.
- `TomadorDto` currently supports CPF only; CNPJ service takers are not modeled.
- Service values support only the base service amount. Detailed tax, discount,
  retention, and address fields are not modeled yet.

### Emission Response

`EmitirNfseResponse` returns:

- `emissionId`: internal UUID.
- `status`: local `EmissionStatus`.
- `protocolo`: provider protocol when known.
- `chaveAcesso`: NFS-e access key when authorized.

### Status Response

`ConsultarStatusResponse` returns:

- `status`.
- `protocolo`.
- `chaveAcesso`.
- `mensagem`.

## Status Model

`EmissionStatus` values:

- `RECEIVED`: request was accepted internally.
- `SIGNED`: DPS XML was built, validated, signed, and persisted.
- `SENT`: generic provider-sent state for unrecognized provider status.
- `PROCESSING`: provider indicates the emission is still processing.
- `AUTHORIZED`: provider returned an access key or authorization status.
- `REJECTED`: provider rejected the emission.
- `CANCELLED`: provider indicates cancellation.
- `FAILED`: local/provider error occurred during emission.

Provider statuses are mapped in `NfseEmissionService.mapStatus`. The mapper
currently recognizes Portuguese/English strings and code `100` for authorized.
Unknown non-null statuses become `SENT`; null status becomes `PROCESSING`.

## Persistence

The only current business table is `nfse_emission`, created by
`V1__create_nfse_emission.sql`.

Important columns:

- `id`: internal UUID primary key.
- `idempotency_key`: unique key used to prevent duplicate sends.
- `provider`: provider code, currently `nacional`.
- `municipality_code`: IBGE municipality code used for emission.
- `provider_protocol`: national provider protocol when returned.
- `access_key`: NFS-e key when authorized.
- `status`: local status enum as text.
- `issuer_cnpj`, `service_taker_document`, `service_amount`: searchable
  request summary fields.
- `dps_xml`: generated unsigned DPS XML.
- `signed_xml`: signed DPS XML sent to provider.
- `response_payload`: provider message or alert description.
- `error_code`, `error_message`: local failure details.
- `created_at`, `updated_at`, `emitted_at`: lifecycle timestamps.

Indexes exist for `status` and `provider_protocol`. The idempotency constraint
is a database unique constraint, not only an application-level check.

## Configuration

Main configuration is in `src/main/resources/application.yml`.

Database:

- `spring.datasource.url`, `username`, `password`.
- `spring.jpa.hibernate.ddl-auto=validate`.
- `spring.flyway.enabled=true`.

Municipality:

- `nfse.municipio.codigo-ibge`, default `3106200`.
- `nfse.municipio.nome`, default `Belo Horizonte`.
- `nfse.municipio.uf`, default `MG`.

Certificate:

- `NFSE_CERTIFICATE_PFX_PATH` maps to `nfse.certificate.pfx-path`.
- `NFSE_CERTIFICATE_PASSWORD` maps to `nfse.certificate.password`.
- `NFSE_CERTIFICATE_ALIAS` optionally chooses a key alias.

National API:

- `NFSE_NATIONAL_API_BASE_URL` maps to `nfse.national-api.base-url`.
- `NFSE_NATIONAL_API_EMISSION_PATH` maps to emission path.
- `NFSE_NATIONAL_API_STATUS_PATH_TEMPLATE` maps to status path template.
- `connect-timeout`, `response-timeout`, and `max-in-memory-size` configure
  the `WebClient`.

Emission defaults:

- `default-provider`: currently `nacional`.
- `ambiente-codigo`: `1` for production, `2` for restricted production /
  homologation according to the current layout assumption.
- `schema-path`: DPS XSD path.
- `leiaute-versao`, `versao-aplicativo`, `serie-dps`.
- Tax/regime defaults used while building the DPS XML.

`idempotency-window` exists in configuration, but it is not currently enforced
by code. Idempotency is permanent unless records are deleted or logic changes.

## Local Development

Start PostgreSQL:

```bash
docker compose up -d
```

Run the application:

```bash
export NFSE_CERTIFICATE_PFX_PATH=/path/to/certificate-a1.pfx
export NFSE_CERTIFICATE_PASSWORD=secret
export NFSE_NATIONAL_API_BASE_URL=https://adn.producaorestrita.nfse.gov.br/contribuintes
mvn spring-boot:run
```

Run tests:

```bash
mvn test
```

Useful local HTTP examples are in `docs/http/emissao.http`.

Important local assumption: application startup creates a `CertificateMaterial`
bean by loading the configured `.pfx`. Without a valid certificate path/password,
the full Spring context may fail to start.

## XML Generation Details

`DpsXmlBuilder` builds a DOM document manually. It is deliberately simple and
should be treated as MVP code.

Current DPS behavior:

- Namespace: `http://www.sped.fazenda.gov.br/nfse`.
- Root element: `DPS` with configured `versao`.
- `infDPS/@Id` is generated from municipality code, issuer document type,
  issuer document, DPS series, and DPS number.
- `dhEmi` uses `America/Sao_Paulo` and subtracts two minutes from current time.
- `serie` comes from `nfse.emission.serie-dps`, normalized by removing leading
  zeroes.
- `nDPS` comes from digits in `idempotencyKey`; if no digits exist, it uses a
  CRC32-derived number.
- National service code accepts either 4 digits and appends `00`, or 6 digits.
- Municipal taxation code is optional, but when provided must normalize to 3
  digits.
- Service amount is rounded to two decimal places with `HALF_UP`.

Recommended future direction: replace or heavily reinforce this manual builder
with generated JAXB/classes from the official and current XSDs, plus broader
schema-focused tests.

## XML Validation and Signature

`NfseXmlValidator` loads the configured XSD from the classpath and validates XML
using `SchemaFactory`. External DTD access is disabled and external schema
access is limited to local file/jar schema references so bundled XSD includes
can resolve.

`SantuarioXmlSignatureService`:

- Parses XML with namespace awareness and disables external entity features.
- Finds the element whose `Id` equals the generated DPS id and marks that
  attribute as an ID.
- Creates an enveloped RSA-SHA256 XMLDSig signature.
- Uses SHA-256 digest and canonicalization without comments.
- Adds the X509 certificate in `KeyInfo`.
- Signs with the private key loaded from the A1 certificate.

Any signing failure is wrapped in `XmlSignatureException`.

## Provider Integration

The provider abstraction is `NfseProvider`:

- `code()`.
- `emit(EmitirNfseRequest, signedXml)`.
- `consultStatus(protocolo)`.

`NfseProviderRegistry` collects all provider beans into an immutable map keyed
by `code()`.

The only implemented provider is `NacionalNfseProvider`:

- Provider code: `nacional`.
- Emission payload: `NationalEmissionPayload(dpsXmlGZipB64)`.
- Compression: `CompressionUtils.gzipBase64`.
- HTTP client: `NacionalNfseClient`.

The national client:

- Sends `POST` to `nfse.national-api.emission-path`.
- Sends header `Idempotency-Key` with the request idempotency key.
- Sends `GET` to `nfse.national-api.status-path-template`.
- Converts HTTP error responses into `NfseProviderException`.
- Deserializes unknown provider response fields leniently using
  `@JsonIgnoreProperties(ignoreUnknown = true)`.

## Error Handling

`GlobalExceptionHandler` returns:

- `400 VALIDATION_ERROR` for validation and malformed WebFlux input errors.
- `404 NOT_FOUND` for `ProviderNotFoundException` and `IllegalArgumentException`.
- `500 INTERNAL_ERROR` for everything else.

Be careful when throwing `IllegalArgumentException`: today it is mapped to
`404`, even if the error is actually invalid input or XML validation. This is a
known behavior to consider before adding new validation paths.

Provider failures during emission are caught by `NfseEmissionService.emit`,
persisted as `FAILED` when an idempotency record exists, and rethrown as
`IllegalStateException("Falha na emissao de NFS-e", ex)`.

## Logging and Correlation

`CorrelationIdWebFilter` reads `X-Correlation-Id` or creates a UUID and returns
it in the response header. It stores the value in SLF4J MDC as
`correlationId`.

Current local log pattern includes `traceId` and `spanId`, but not
`correlationId`. If correlation-id visibility is required in local logs, update
`logback-spring.xml`.

## Tests

Current automated coverage is focused on `DpsXmlBuilderTest`:

- Builds a sample DPS XML.
- Validates it against the bundled official DPS schema.
- Asserts generated DPS id shape and key XML fields.

Test gaps to keep in mind:

- `NfseEmissionService` idempotency and failure handling.
- Provider status mapping.
- National client error handling.
- Certificate loading edge cases.
- Signature structure and reference behavior.
- Controller validation/error responses.
- Persistence migration/entity alignment.

## How To Add A Feature Safely

For API/request changes:

1. Update DTO records and validation annotations.
2. Update `DpsXmlBuilder` only if the new field affects DPS XML.
3. Add XSD validation tests for any XML shape changes.
4. Update `docs/http/emissao.http` and this knowledge base.

For new provider behavior:

1. Implement `NfseProvider`.
2. Return a unique `code()`.
3. Add provider-specific client/config classes if needed.
4. Configure `nfse.emission.default-provider`.
5. Add tests for provider mapping and registry behavior.

For persistence changes:

1. Add a new Flyway migration; do not edit an already-applied migration.
2. Update `NfseEmissionEntity`.
3. Verify Hibernate validation still passes on startup.
4. Add repository/service tests when behavior changes.

For fiscal XML changes:

1. Confirm the current official national NFS-e documentation and XSD version.
2. Prefer typed XML generation from XSD/JAXB or a strongly tested mapper.
3. Validate both unsigned and signed XML.
4. Add tests with realistic CPF/CNPJ, municipal code, service code, tax regime,
   and value combinations.

For asynchronous processing:

1. Avoid blocking provider calls inside database transactions.
2. Preserve idempotency semantics.
3. Consider outbox/event table for retryable provider communication.
4. Persist all provider requests/responses needed for audit and support.

## Known Gaps And Risks

- The project stores full unsigned and signed XML in the database. This may be
  correct for audit, but it has privacy/security implications and may need
  encryption, retention policies, and access controls.
- `certs/` exists locally and may contain real `.pfx` material. Certificates
  must not be committed and should be supplied through secure volumes or secret
  managers in production.
- `idempotency-window` is configured but unused.
- `DpsXmlBuilder` is manual MVP code and does not cover the full DPS/NFS-e
  domain.
- Tomador support is CPF-only in the public DTO.
- Emission is synchronous from API request to provider call. There is no queue,
  outbox, retry policy, or scheduled polling yet.
- Provider response mapping is permissive and may not cover all official return
  formats.
- Error classification currently maps all `IllegalArgumentException` instances
  to `404 NOT_FOUND`.
- Local logs do not currently print `correlationId` even though the filter sets
  it in MDC.
- The bundled XSD version is `nfse-v1.01`; always verify official current
  documentation before fiscal changes.

## Current Mental Model

Think of the application as an NFS-e emission orchestrator:

```text
HTTP API
  -> validate request
  -> find/create emission by idempotency key
  -> build DPS XML
  -> validate DPS XML
  -> sign XML with A1 certificate
  -> validate signed XML
  -> persist signed emission
  -> gzip/base64 XML
  -> send to national provider over mTLS
  -> map provider response
  -> persist protocol/status/access key
  -> return internal response
```

The safest place to evolve business behavior is `NfseEmissionService`; the
safest place to evolve national provider HTTP behavior is
`NacionalNfseClient`/`NacionalNfseProvider`; the highest-risk place to change is
`DpsXmlBuilder`, because small XML changes can break official schema validation
or provider acceptance.

## Planned Evolution

- Replace `DpsXmlBuilder` with typed generation via XSD/JAXB.
- Add outbox and queue for retries, status polling, and cancellation.
- Include a table of fiscal events and payload auditing.
- Add providers by municipality when there are particularities outside the national standard.
- Externalize secrets in Vault/KMS/secret manager.

## Last Updates

| Date | Title | Version | Details |
| --- | --- | --- | --- |
| 2026-08-07 | Documentation baseline and update history | 0.1.0 | [Project knowledge base summary](docs/PROJECT_KNOWLEDGE_BASE.md#2026-08-07---documentation-baseline-and-update-history) |
