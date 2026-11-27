# V1 acceptance tests

These tests check behavior through the public HTTP API with the application's production
configuration and modules. They exercise the real request and document flows, including
authorization, database persistence, PDF creation, and signing. They complement—not
replace—focused route, handler, domain, and repository tests.

## What runs locally

`ProductionApp` hosts the application in Ktor's test application using `application.yaml`.
Testcontainers starts PostgreSQL (migrated with Liquibase), a Vault Transit mock, a test
token service, and WireMock for external HTTP services. Tests use real API calls and
explicitly stub the external responses and PDP decisions each scenario needs. An actor
declaration alone does not register an external service response.

## Where tests belong

- `requests/common` and `documents/common`: behavior shared by request or document
  types, such as authentication, representation, visibility, and approval or signing.
- Type-specific directories: the type's own payload, scope, validity, PDF content, and
  resulting grant behavior.
- Focused tests elsewhere in `v1`: detailed validation matrices, failure propagation,
  internal routing, and repository edge cases.

In each scenario, declare the actors, configure only the external responses and permissions
needed, call the API, and assert observable response or persisted behavior. Keep the
expected contract visible in the test rather than hiding it in setup helpers.

## Isolation and execution

The database data and WireMock mappings reset before every test. Acceptance specs share
containers and temporarily set JVM system properties, so `acceptanceLifecycle` serializes
specs within a JVM. Do not enable concurrent execution of individual tests in a spec
without isolating those shared resources first.

Run the common suites with Docker available:

```sh
./gradlew test --tests 'no.elhub.auth.v1.acceptance.requests.common.CommonRequestBehaviorAcceptanceTest'
./gradlew test --tests 'no.elhub.auth.v1.acceptance.documents.common.CommonDocumentBehaviorAcceptanceTest'
```

To run all acceptance specs, use `./gradlew test --tests 'no.elhub.auth.v1.acceptance.*'`.
