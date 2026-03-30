---
name: testing-kotest
description: >
  Use when writing any test. Defines FunSpec structure, MockK patterns for suspend functions, clearMocks usage,
  Arrow assertions, route test wiring, and integration test extensions.
  Load before generating any test class.
---
# Testing with Kotest

All tests use `FunSpec`. All mocking uses MockK.

## Handler tests

```kotlin
class CreateHandlerTest : FunSpec({
    val repo = mockk<RequestRepository>()
    val handler = CreateHandler(repo)

    beforeTest { clearMocks(repo) }   // required when mocks declared at spec level

    test("returns Right when valid") {
        coEvery { repo.insert(any()) } returns createdRequest.right()
        handler(validModel).shouldBeRight()
    }

    test("returns Left(PersistenceError) when repo fails") {
        coEvery { repo.insert(any()) } returns RepositoryWriteError.UnexpectedError.left()
        handler(validModel).shouldBeLeft(CreateError.PersistenceError)
    }
})
```

- Use `coEvery` / `coVerify` for `suspend` functions — `every` silently fails to stub them.
- Always `clearMocks(...)` in `beforeTest` when mocks are shared across tests.

## Route tests

Use `setupAppWith { route(handler, authProvider) }` from test utilities:

```kotlin
class CreateRouteTest : FunSpec({
    val authProvider = mockk<AuthorizationProvider>()
    val handler = mockk<CreateHandler>()

    beforeTest { clearMocks(authProvider, handler) }

    test("POST returns 201 when handler succeeds") {
        coEvery { authProvider.authorizeMaskinporten(any()) } returns actor.right()
        coEvery { handler(any()) } returns createdRequest.right()

        testApplication {
            setupAppWith { route(handler, authProvider) }
            val response = client.post("/path") {
                contentType(ContentType.parse("application/vnd.api+json"))
                setBody(validJson)
            }
            response.status shouldBe HttpStatusCode.Created
        }
    }
})
```

## Repository (integration) tests

```kotlin
class MyRepositoryTest : FunSpec({
    extensions(PostgresTestContainerExtension())

    val repo = ExposedMyRepository()

    beforeSpec {
        Database.connect(PostgresTestContainer.JDBC_URL, PostgresTestContainer.DRIVER,
            PostgresTestContainer.USERNAME, PostgresTestContainer.PASSWORD)
    }

    beforeTest { transaction { MyTable.deleteAll() } }

    test("find returns Left(NotFoundError) when row missing") {
        repo.find(UUID.randomUUID()).shouldBeLeft(RepositoryReadError.NotFoundError)
    }
})
```

`PostgresTestContainerExtension` spins up Postgres and runs all Liquibase migrations.

## Test helpers

| Helper | Purpose |
| ------ | ------- |
| `setupAppWith { }` | Wires a minimal Ktor app for route tests |
| `putPdf(path, bytes)` | HTTP PUT with `application/pdf` |
| `PostgresTestContainerExtension` | Full DB with migrations |
| `RunPostgresScriptExtension(sql)` | Seed SQL runner |
| `TestCertificateFactory` | Generates test Maskinporten JWTs |

## Assertions

```kotlin
result.shouldBeRight()
result.shouldBeLeft(SpecificError)
result.shouldBeRight()  // then: result.getOrNull()!!.field shouldBe expected
response.status shouldBe HttpStatusCode.OK
```
