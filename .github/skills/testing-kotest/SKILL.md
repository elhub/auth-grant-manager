---
name: testing-kotest
description: >
  Use when writing any test. Defines FunSpec structure, MockK patterns for suspend functions, clearMocks usage,
  value and exception assertions, route test wiring, and integration test extensions.
  Load before generating any test class.
---

# Testing with Kotest

All tests use Kotest `FunSpec`. All mocking uses MockK. Integration tests use TestContainers.
Expected domain and repository failures use custom exceptions; success returns ordinary values. Do not use Arrow, Either, or result wrappers.

## Structure

```kotlin
import io.kotest.core.spec.style.FunSpec

class MyFeatureTest : FunSpec({
    // tests go here
})
```

No other Kotest spec style is used in this project.

## Unit tests (Handlers)

Mock dependencies at the constructor. Use `coEvery` for `suspend` functions (not `every`).

```kotlin
import io.kotest.assertions.throwables.shouldThrow

class CreateHandlerTest : FunSpec({
    val repo = mockk<RequestRepository>()
    val partyService = mockk<PartyService>()
    val handler = CreateHandler(repo, partyService)

    // Reset mocks between tests — required when mockks are declared at spec level
    beforeTest {
        clearMocks(repo, partyService)
    }

    test("returns success when request is valid") {
        coEvery { partyService.resolve(any()) } returns validParty
        coEvery { repo.insert(any()) } returns createdRequest

        handler(validModel) shouldBe createdRequest
    }

    test("throws an authorization error when party mismatch") {
        coEvery { partyService.resolve(any()) } returns mismatchedParty

        shouldThrow<CreateError.AuthorizationError> { handler(validModel) }
    }

    test("propagates a conflict when repo fails") {
        coEvery { partyService.resolve(any()) } returns validParty
        coEvery { repo.insert(any()) } throws RepositoryWriteError.ConflictError("Request already exists")

        shouldThrow<RepositoryWriteError.ConflictError> { handler(validModel) }
    }
})
```

**Rules:**

- Assert ordinary success values with `shouldBe` and custom failures with `shouldThrow<...>`.
- `CreateError` types here are illustrative custom exception subclasses; use the feature's actual exceptions.
- Always call `clearMocks(...)` in `beforeTest` when mocks are shared across tests
- Use `coEvery` for all `suspend` functions, including `coEvery { ... } throws ...` for failures.

## Route tests

Mock the `Handler` and `AuthorizationProvider`. Use `testApplication`.

```kotlin
class CreateRouteTest : FunSpec({
    val authProvider = mockk<AuthorizationProvider>()
    val handler = mockk<CreateHandler>()

    beforeTest { clearMocks(authProvider, handler) }

    test("POST returns 201 when handler succeeds") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedActor
        coEvery { handler(any()) } returns createdRequest

        testApplication {
            application {
                configureErrorHandling()
                createRouteModule(handler, authProvider)
            }

            val response = client.post(REQUESTS_PATH) {
                contentType(ContentType.parse("application/vnd.api+json"))
                setBody(validCreateRequestJson)
            }

            response.status shouldBe HttpStatusCode.Created
            response.bodyAsText() shouldContain "\"type\":\"AuthorizationRequest\""
        }
    }

    test("POST returns 409 when handler throws a conflict") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedActor
        coEvery { handler(any()) } throws RepositoryWriteError.ConflictError("Request already exists")

        testApplication {
            application {
                configureErrorHandling()
                createRouteModule(handler, authProvider)
            }

            val response = client.post(REQUESTS_PATH) {
                contentType(ContentType.parse("application/vnd.api+json"))
                setBody(validCreateRequestJson)
            }

            response.status shouldBe HttpStatusCode.Conflict
            response.bodyAsText() shouldContain "\"errors\""
        }
    }
})
```

Route tests assert on `status` and JSON:API response shape — not on Handler internals.
Install the application's `configureErrorHandling()` to verify thrown exceptions map to HTTP responses, rather than adding test-only mappings.

## Integration tests

```kotlin
class CreateRequestIntegrationTest : FunSpec({
    extensions(
        PostgresTestContainerExtension(),
        RunPostgresScriptExtension("db/seed-parties.sql")
    )

    test("creates and retrieves a request end to end") {
        testApplication {
            val createResponse = client.post(REQUESTS_PATH) {
                contentType(ContentType.parse("application/vnd.api+json"))
                setBody(validCreateRequestJson)
            }
            createResponse.status shouldBe HttpStatusCode.Created

            val id = createResponse.bodyAsText().extractId()

            val getResponse = client.get("$REQUESTS_PATH/$id")
            getResponse.status shouldBe HttpStatusCode.OK
        }
    }
})
```

`PostgresTestContainerExtension` spins up Postgres and runs all Liquibase migrations before any test in the class. `RunPostgresScriptExtension` seeds additional
fixtures.

## Test helpers

Located in `no.elhub.auth.features.common` test sources:

| Helper                           | Purpose                          |
|----------------------------------|----------------------------------|
| `TestCertificateFactory`         | Generates test Maskinporten JWTs |
| `AuthPersonsTestContainer`       | External person-lookup stub      |
| `PostgresTestContainerExtension` | Full DB with migrations          |
| `RunPostgresScriptExtension`     | Seed SQL runner                  |

## Assertions reference

```kotlin
// Ordinary success value
handler(validModel) shouldBe expected

// Expected custom exception
shouldThrow<RepositoryReadError.NotFoundError> { repo.find(nonExistentId) }

// HTTP
response.status shouldBe HttpStatusCode.OK
response.bodyAsText().shouldNotBeEmpty()

// General
value shouldBe expected
list shouldHaveSize 3
str shouldContain "substring"
```
