---
name: testing-layers
description: >
  Use when writing tests for Routes, Handlers, Services, or Repositories.
  Defines how each layer is tested in isolation and how e2e tests wire the full stack.
  Load alongside testing-kotest and testing-anti-patterns before writing any test class.
---
# Testing Layers

Each layer is tested in isolation. Never test two layers together unless it's an e2e test.

## Summary

| Layer | Scope | Mocked | Key assertion |
| ----- | ----- | ------ | ------------- |
| Route | HTTP request/response | Handler, AuthProvider | Status code, content-type, error body shape |
| Handler | Business logic | Repository, Services | `shouldBeLeft(SpecificError)` / `shouldBeRight()` |
| Repository | DB queries | Nothing (real Postgres) | Returned domain objects or typed errors |
| E2E | All layers | Nothing | Full request flow against real DB |

## Route tests

Mock the handler and auth provider. Assert on HTTP concerns only — status, headers, body shape.

```kotlin
test("PATCH returns 200 with response body on success") {
    coEvery { authProvider.authorizeMaskinporten(any()) } returns actor.right()
    coEvery { handler(any()) } returns updatedFoo.right()

    testApplication {
        setupAppWith { updateFooRoute(handler, authProvider) }
        val response = client.patch("/access/v0/foos/$id") {
            contentType(ContentType.parse("application/vnd.api+json"))
            setBody(validPatchJson)
        }
        response.status shouldBe HttpStatusCode.OK
        response.contentType()?.match(ContentType.parse("application/vnd.api+json")) shouldBe true
    }
}
```

**Assert in route tests:** status code, content-type, error response body, required headers.
**Do not assert:** handler invocation count, domain field values inside the body.

## Handler tests

Mock all dependencies. Assert only on the returned `Either` value.

```kotlin
test("returns Left(AuthorizationError) when actor party does not match") {
    coEvery { repo.find(id) } returns existing.right()
    val result = handler(Command(actor = mismatchedActor, id = id))
    result.shouldBeLeft(UpdateFooError.AuthorizationError)
}
```

**Assert in handler tests:** which `Left` error is returned, which `Right` value is returned.
**Do not assert:** repository call counts, HTTP status.

## Repository tests (integration)

Use `PostgresTestContainerExtension`. No mocking. Test against a real DB.

```kotlin
test("update returns Right(updated) when row exists") {
    transaction { FooTable.insert { it[id] = testId; it[bar] = "a"; it[zoo] = "b"; it[ok] = "c" } }
    val result = repo.update(testId, bar = "new")
    result.shouldBeRight { it.bar shouldBe "new" }
}
```

**Assert in repository tests:** returned domain object fields, typed errors (NotFoundError, etc.).
**Do not assert:** SQL implementation details.
