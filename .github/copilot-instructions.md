# GitHub Copilot Instructions — auth-grant-manager

`auth-grant-manager` is a Kotlin/Ktor REST API for managing authorization grants.

## Stack

| Concern        | Library                               |
|----------------|---------------------------------------|
| Language       | Kotlin, JVM 21+                       |
| Framework      | Ktor (server + client)                |
| Error handling | Arrow (`Either`, `raise` DSL)         |
| DI             | Koin + KSP                            |
| Database       | PostgreSQL, Exposed ORM, Liquibase    |
| API standard   | JSON:API (`application/vnd.api+json`) |
| Serialization  | Kotlinx Serialization                 |
| Docs           | OpenAPI / Swagger                     |

## Non-negotiable constraints

- All IO is `suspend`. Never use `runBlocking` in production code.
- All business logic returns `Either<Error, Success>`. No exceptions for domain errors.
- Use the `raise` DSL (`either { ... }`) and `bind()` throughout. No imperative unwrapping.
- Domain errors are sealed interfaces, one per action slice.
- Prefer immutable data classes.

## Skills

Load the relevant skill before generating code. Skills are located alongside this file.

| Task                                       | Skill                                      |
|--------------------------------------------|--------------------------------------------|
| Any Handler, Service, or Repository        | `functional-error-handling-arrow`          |
| Any new feature, action slice, or module   | `vertical-slice-architecture`              |
| Any Repository, Table object, or migration | `database-access-exposed-liquibase`        |
| Any Route, DTO, or error response          | `json-api-compliance`                      |
| Any test class                             | `testing-kotest` + `testing-anti-patterns` |
| Starting any implementation                | `test-driven-development`                  |

When a task touches multiple concerns, load all relevant skills before starting.

## Workflow

1. Load the relevant skill(s).
2. Find the existing pattern in the codebase before generating anything new.
3. Write the failing test first (`testing-kotest`, `test-driven-development`).
4. Implement to pass the test.
5. All IO: `suspend`. No `runBlocking`.
