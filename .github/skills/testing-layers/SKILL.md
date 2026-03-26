---
name: testing-layers
description: >
  Use when writing tests for Routes, Handlers, Services, or Repositories.
  Defines how each layer is tested in isolation and how e2e tests wire the full stack.
  Load alongside testing-kotest and testing-anti-patterns before writing any test class.
---

# Testing Layers

Each layer is tested in isolation. Dependencies are mocked at the boundary. E2E tests run through the full stack without mocks.

## Route tests

Routes are tested in isolation using Ktor's `testApplication`.
The Handler is mocked. The test verifies HTTP mechanics: status codes, headers, and JSON:API response shape — not business logic.

```kotlin
class CreateRouteTest : FunSpec({
    val handler = mockk<CreateHandler>()

    beforeTest { clearMocks(handler) }

    test("responds 201 when handler returns Right") {
        coEvery { handler.invoke(any()) } returns CreatedGrant(...).right()

        testApplication {
            application { configureCreateRoute(handler) }
            val response = client.post("/grants") {
                contentType(ContentType.Application.Json)
                setBody(validRequestBody)
            }
            response.status shouldBe HttpStatusCode.Created
        }
    }

    test("responds 422 when handler returns Left(ValidationError)") {
        coEvery { handler.invoke(any()) } returns CreateError.ValidationError("bad input").left()

        testApplication {
            application { configureCreateRoute(handler) }
            val response = client.post("/grants") {
                contentType(ContentType.Application.Json)
                setBody(validRequestBody)
            }
            response.status shouldBe HttpStatusCode.UnprocessableEntity
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
 The test verifies orchestration logic: which dependencies are called, in what order, and what Either value is returned.
```kotlin
class CreateHandlerTest : FunSpec({
val repo = mockk<GrantRepository>()
val partyService = mockk<PartyService>()
val handler = CreateHandler(repo, partyService)

    beforeTest { clearMocks(repo, partyService) }

    test("returns Right(CreatedGrant) when all dependencies succeed") {
        coEvery { partyService.resolve(any()) } returns validParty.right()
        coEvery { repo.insert(any()) } returns createdGrant.right()

        handler.invoke(validModel).shouldBeRight(createdGrant)
    }

    test("returns Left(AuthorizationError) when party does not match model") {
        coEvery { partyService.resolve(any()) } returns mismatchedParty.right()

        handler.invoke(validModel).shouldBeLeft(CreateError.AuthorizationError)

        coVerify(exactly = 0) { repo.insert(any()) }
    }

    test("returns Left(RepositoryError) when repo fails") {
        coEvery { partyService.resolve(any()) } returns validParty.right()
        coEvery { repo.insert(any()) } returns CreateError.RepositoryError.left()

        handler.invoke(validModel).shouldBeLeft(CreateError.RepositoryError)
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
## Repository Tests
Repositories are tested against a real database. Use a test database extension that rolls back after each test.
Do not mock the database — that defeats the purpose of a repository test.
```kotlin
class GrantRepositoryTest : FunSpec({
extensions(TestDatabaseExtension)

    val repo = GrantRepository()

    test("insert returns Right(Grant) with a generated id") {
        val result = repo.insert(newGrantData)

        result.shouldBeRight()
        result.getOrNull()?.id shouldNotBe null
    }

    test("findById returns Left(NotFound) when row does not exist") {
        repo.findById(nonExistentId).shouldBeLeft(GrantError.NotFound)
    }

    test("insert returns Left(Conflict) when duplicate key exists") {
        repo.insert(newGrantData)
        repo.insert(newGrantData).shouldBeLeft(GrantError.Conflict)
    }
})
```
What to not to assert in repository test:
- HTTP status code and response body shape
- Database state after the request
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

    test("returns Right(Party) when client returns a valid response") {
        coEvery { client.fetch(partyId) } returns partyResponse.right()

        service.resolve(partyId).shouldBeRight(expectedParty)
    }

    test("returns Left(NotFound) when client returns 404") {
        coEvery { client.fetch(partyId) } returns PartyError.NotFound.left()

        service.resolve(partyId).shouldBeLeft(PartyError.NotFound)
    }
})
```
What to assert in service tests:
- The Either value returned
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
| Handler    | All mocked       | No       | Orchestration, Either value, call order   |
| Service    | All mocked       | No       | Domain rules, transformations             |
| Repository | None mocked      | Real     | SQL correctness, constraint mapping       |
| E2E        | Nothing mocked   | Real     | Full observable behaviour from outside   |
