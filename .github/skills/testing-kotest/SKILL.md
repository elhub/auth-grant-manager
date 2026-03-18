---
name: testing-kotest
description: Use when writing any test. Defines FunSpec structure, MockK patterns for suspend functions, clearMocks usage, Arrow assertions, route test wiring, and integration test extensions. Load before generating any test class.
---

# Testing with Kotest

All tests use Kotest `FunSpec`. All mocking uses MockK. Integration tests use TestContainers.

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
class CreateHandlerTest : FunSpec({
    val repo = mockk<RequestRepository>()
    val partyService = mockk<PartyService>()
    val handler = CreateHandler(repo, partyService)

    // Reset mocks between tests — required when mockks are declared at spec level
    beforeTest {
        clearMocks(repo, partyService)
    }

    test("returns Right when request is valid") {
        coEvery { partyService.resolve(any()) } returns validParty.right()
        coEvery { repo.insert(any()) } returns createdRequest.right()

        handler(validModel).shouldBeRight()
    }

    test("returns Left(AuthorizationError) when party mismatch") {
        coEvery { partyService.resolve(any()) } returns mismatchedParty.right()

        handler(validModel).shouldBeLeft(CreateError.AuthorizationError)
    }

    test("returns Left(PersistenceError) when repo fails") {
        coEvery { partyService.resolve(any()) } returns validParty.right()
        coEvery { repo.insert(any()) } returns RepositoryWriteError.UnexpectedError.left()

        handler(validModel).shouldBeLeft(CreateError.PersistenceError)
    }
})
```

**Rules:**

- Use `shouldBeRight()` / `shouldBeLeft()` from `io.kotest.assertions.arrow.core`
- Always call `clearMocks(...)` in `beforeTest` when mocks are shared across tests
- Use `coEvery` for all `suspend` functions — `every` will silently fail to stub them

## Route tests

Mock the `Handler` and `AuthorizationProvider`. Use `testApplication`.

```kotlin
class CreateRouteTest : FunSpec({
    val authProvider = mockk<AuthorizationProvider>()
    val handler = mockk<CreateHandler>()

    beforeTest { clearMocks(authProvider, handler) }

    test("POST returns 201 when handler succeeds") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns authorizedActor.right()
        coEvery { handler(any()) } returns createdRequest.right()

        testApplication {
            application { createRouteModule(handler, authProvider) }

            val response = client.post(REQUESTS_PATH) {
                contentType(ContentType.parse("application/vnd.api+json"))
                setBody(validCreateRequestJson)
            }

            response.status shouldBe HttpStatusCode.Created
            response.bodyAsText() shouldContain "\"type\":\"AuthorizationRequest\""
        }
    }

    test("POST returns 403 when auth fails") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns AuthError.Unauthorized.left()

        testApplication {
            application { createRouteModule(handler, authProvider) }

            val response = client.post(REQUESTS_PATH) {
                contentType(ContentType.parse("application/vnd.api+json"))
                setBody(validCreateRequestJson)
            }

            response.status shouldBe HttpStatusCode.Forbidden
        }
    }
})
```

Route tests assert on `status` and JSON:API response shape — not on Handler internals.

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
// Arrow
result.shouldBeRight()
result.shouldBeLeft()
result.shouldBeRight { value -> value.id shouldBe expectedId }

// HTTP
response.status shouldBe HttpStatusCode.OK
response.bodyAsText().shouldNotBeEmpty()

// General
value shouldBe expected
list shouldHaveSize 3
str shouldContain "substring"
```
