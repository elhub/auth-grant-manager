---
name: testing-layers
description: >
  Use when writing tests for Routes, Handlers, Services, or Repositories.
  Defines how each layer is tested in isolation and how e2e tests wire the full stack.
  Load alongside testing-kotest and testing-anti-patterns before writing any test class.
---

# Testing Layers

Each layer is tested in isolation. Dependencies are mocked at the boundary. E2E tests run through the full stack without mocks.
Expected domain and repository failures use custom exceptions; success returns ordinary values. Do not use Arrow, Either, or result wrappers.

## Route tests

Routes are tested in isolation using Ktor's `testApplication`.
The Handler is mocked. The test verifies HTTP mechanics: status codes, headers, and JSON:API response shape — not business logic.
Install the application's `configureErrorHandling()` so thrown exceptions are tested through the real HTTP error mapping.

```kotlin
class CreateRouteTest : FunSpec({
    val handler = mockk<CreateHandler>()

    beforeTest { clearMocks(handler) }

    test("responds 201 when handler succeeds") {
        coEvery { handler.invoke(any()) } returns createdGrant

        testApplication {
            application {
                configureErrorHandling()
                configureCreateRoute(handler)
            }
            val response = client.post("/grants") {
                contentType(ContentType.Application.Json)
                setBody(validRequestBody)
            }
            response.status shouldBe HttpStatusCode.Created
        }
    }

    test("responds 409 when handler throws a conflict") {
        coEvery { handler.invoke(any()) } throws RepositoryWriteError.ConflictError("Grant already exists")

        testApplication {
            application {
                configureErrorHandling()
                configureCreateRoute(handler)
            }
            val response = client.post("/grants") {
                contentType(ContentType.Application.Json)
                setBody(validRequestBody)
            }
            response.status shouldBe HttpStatusCode.Conflict
            response.bodyAsText() shouldContain "\"errors\""
        }
    }
})
```

What to assert in route tests:
- HTTP status code
- Content-Type: application/vnd.api+json
- Top-level JSON:API shape (data, errors)
- That the handler was called with the correct parsed model (coVerify)

What not to assert:
- business logic
- database state
- error message content beyond the JSON:API error object.

## Handler tests

Handlers are tested in isolation. All dependencies (Services, Repositories) are mocked.
The test verifies orchestration logic: which dependencies are called, in what order, and what value is returned or exception is thrown.
```kotlin
import io.kotest.assertions.throwables.shouldThrow

class CreateHandlerTest : FunSpec({
val repo = mockk<GrantRepository>()
val partyService = mockk<PartyService>()
val handler = CreateHandler(repo, partyService)

    beforeTest { clearMocks(repo, partyService) }

     test("returns a created grant when all dependencies succeed") {
         coEvery { partyService.resolve(any()) } returns validParty
         coEvery { repo.insert(any()) } returns createdGrant

        handler.invoke(validModel) shouldBe createdGrant
    }

     test("throws an authorization error when party does not match model") {
         coEvery { partyService.resolve(any()) } returns mismatchedParty

        shouldThrow<CreateError.AuthorizationError> { handler.invoke(validModel) }

        coVerify(exactly = 0) { repo.insert(any()) }
    }

     test("propagates a conflict when repo fails") {
         coEvery { partyService.resolve(any()) } returns validParty
         coEvery { repo.insert(any()) } throws RepositoryWriteError.ConflictError("Grant already exists")

        shouldThrow<RepositoryWriteError.ConflictError> { handler.invoke(validModel) }
    }
})
```
`CreateError` types here are illustrative custom exception subclasses; use the feature's actual exceptions.

What to assert in handler tests:
- Ordinary success values or thrown custom exceptions
- Dependency calls and orchestration (coVerify)

What not to assert:
- HTTP status codes and response shape
- database state

## Repository Tests
Repositories are tested against a real database. Use a test database extension with cleanup between tests.
Do not mock the database — that defeats the purpose of a repository test.
```kotlin
class GrantRepositoryTest : FunSpec({
extensions(TestDatabaseExtension)

    val repo = GrantRepository()

     test("insert returns a grant with a generated id") {
        val result = repo.insert(newGrantData)

         result.id.shouldNotBeNull()
         repo.findById(result.id) shouldBe result
    }

     test("findById throws NotFound when row does not exist") {
         shouldThrow<RepositoryReadError.NotFoundError> { repo.findById(nonExistentId) }
    }

     test("insert throws Conflict when duplicate key exists") {
        repo.insert(newGrantData)
         shouldThrow<RepositoryWriteError.ConflictError> { repo.insert(newGrantData) }
    }
})
```
What to assert in repository tests:
- Ordinary success values, persisted rows, and custom exceptions for constraint or lookup failures
- Transaction atomicity using persisted state, not just the thrown exception

For document confirmation, force a dependent grant or property insert to fail after the document status update has executed.
Use a real database constraint violation, not a mocked database or a failure before any write.
After the operation throws, read persisted state in a fresh transaction before test cleanup.
Assert the document status is unchanged and no partial grant or grant properties remain.
Do not wrap the operation and assertions in a test transaction whose rollback could hide a missing production transaction.

What not to assert in repository tests:
- HTTP status code and response body shape
- End to end error responses for known failure paths

Do not test any internal details of the implentation

## Service tests

Services are tested in isolation.
External clients and repositories they depend on are mocked.
The test verifies domain rules and transformations.

```kotlin
class PartyServiceTest : FunSpec({
    val client = mockk<PartyClient>()
    val service = PartyService(client)

    beforeTest { clearMocks(client) }

     test("returns a party when client returns a valid response") {
         coEvery { client.fetch(partyId) } returns partyResponse

        service.resolve(partyId) shouldBe expectedParty
    }

     test("propagates NotFound when client throws it") {
         coEvery { client.fetch(partyId) } throws PartyError.NotFound("Party not found")

        shouldThrow<PartyError.NotFound> { service.resolve(partyId) }
    }
})
```
`PartyError.NotFound` is an illustrative custom exception; use the actual client or domain exception and test any required translation.

What to assert in service tests:
- The ordinary success value or thrown custom exception
- Domain transformations and mapping logic
- Validation rules enforced by the service

What not to assert:
- HTTP status codes
- SQL queries
- Handler orchestration


## Summary

| Layer      | Dependencies     | Database | What is verified                          |
|------------|------------------|----------|-------------------------------------------|
| Route      | Handler mocked   | No       | HTTP mechanics, JSON:API shape            |
| Handler    | All mocked       | No       | Orchestration, outcome, call order        |
| Service    | All mocked       | No       | Domain rules, transformations             |
| Repository | None mocked      | Real     | Persisted state, constraints, atomicity   |
| E2E        | Nothing mocked   | Real     | Full observable behaviour from outside   |
