# GitHub Copilot Instructions — auth-grant-manager

`auth-grant-manager` is a Kotlin/Ktor REST API for managing authorization grants.

## Stack

| Concern        | Library                               |
|----------------|---------------------------------------|
| Language       | Kotlin, JVM 21+                       |
| Framework      | Ktor (server + client)                |
| Error handling | Explicit domain errors               |
| DI             | Ktor Native DI                        |
| Database       | PostgreSQL, Exposed ORM, Liquibase    |
| API standard   | JSON:API (`application/vnd.api+json`) |
| Serialization  | Kotlinx Serialization                 |
| Docs           | OpenAPI / Swagger                     |

## Non-negotiable constraints

- All IO is `suspend`. Never use `runBlocking` in production code.
- Handle expected domain errors explicitly. Do not use exceptions for expected domain failures.
- Keep error handling clear at service and repository boundaries; do not hide control flow behind an abstraction.
- Domain errors are sealed interfaces, one per action slice.
- Prefer immutable data classes.

## Skills

Load the relevant skill before generating code. Skills are located alongside this file.

| Task                                       | Skill                                                        |
|--------------------------------------------|--------------------------------------------------------------|
| Any Handler, Service, or Repository        | `explicit-error-handling`                                    |
| Any new feature, action slice, or module   | `vertical-slice-architecture`                                |
| Any Repository, Table object, or migration | `database-access-exposed-liquibase`                          |
| Any Route, DTO, or error response          | `json-api-compliance`                                        |
| Any test class                             | `testing-kotest` + `testing-anti-patterns` +`testing layers` |
| Starting any implementation                | `test-driven-development`                                    |
| Code reviews and pull request reviews      | `code-review`                                                |

When a task touches multiple concerns, load all relevant skills before starting.

## Workflow

1. Load the relevant skill(s).
2. Find the existing pattern in the codebase before generating anything new.
3. Write the failing test first (`testing-kotest`, `test-driven-development`).
4. Implement to pass the test.
5. All IO: `suspend`. No `runBlocking`.
