---
name: database-access-exposed-liquibase
description: >
  Use when writing or reviewing any Repository, Table object, or migration.
  Defines Exposed table conventions and explicit repository error handling,
  suspending transaction usage, and Liquibase migration rules.
  Load before generating any database access code.
---
# Database Access with Exposed & Liquibase

Repositories return success values and throw custom exceptions for expected failures. Transactions are explicit. Migrations are versioned sequential SQL files.

## Table definitions

Tables are `object`s defined inside the owning feature's `common/` directory. Use `UUIDTable` for entity tables, `Table` for join tables.
- DAO layer is implementented using org.jetbrains.exposed which is an ORM, where UUIDTable is provided jetbrains.

```kotlin
object AuthorizationRequestTable : UUIDTable("auth.authorization_request") {
    val requestType = customEnumeration(
        name = "request_type",
        sql = "auth.authorization_request_type",
        fromDb = { AuthorizationRequest.Type.valueOf(it as String) },
        toDb = { PGEnum("authorization_request_type", it) },
    )
    val requestedBy = javaUUID("requested_by").references(AuthorizationPartyTable.id)
    val createdAt = timestampWithTimeZone("created_at").clientDefault { currentTimeUtc() }
    val updatedAt = timestampWithTimeZone("updated_at").clientDefault { currentTimeUtc() }
    val validTo = timestampWithTimeZone("valid_to")
}

object AuthorizationRequestScopeTable : Table("auth.authorization_request_scope") {
    val authorizationRequestId = javaUUID("authorization_request_id")
        .references(AuthorizationRequestTable.id, onDelete = ReferenceOption.CASCADE)
    val authorizationScopeId = javaUUID("authorization_scope_id")
        .references(AuthorizationScopeTable.id, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(authorizationRequestId, authorizationScopeId)
}
```

**Conventions:**

- All table names are schema-qualified: `"auth.<name>"`
- PostgreSQL enums use `customEnumeration` with `PGEnum`
- Timestamps use `timestampWithTimeZone`; default to `currentTimeUtc()`
- Foreign keys use `.references(OtherTable.id)`

## Repository pattern
- All repositories must have an interface. Then use a concrete implementation of that interface to interact with data source (Postgres).
- Return domain values on success; document expected custom exceptions on repository contracts.
- All repository functions must have suspend keyword.
### Interface (in `features/{domain}/common/`)

```kotlin
interface RequestRepository {
    /** @throws RepositoryReadError.NotFoundError when the request does not exist. */
    suspend fun find(id: UUID): AuthorizationRequest
    suspend fun insert(request: AuthorizationRequest): AuthorizationRequest
}
```

### Implementation
- DAO always happens through org.jetbrains.exposed, do not use SQL queries. Table must have a table type defined through exposed.
- Throw custom repository exceptions for expected failures; let unexpected infrastructure exceptions propagate.
- Follow `functional-error-handling-arrow/SKILL.md` for narrow exception adapters. Do not catch every exception and convert it to `UnexpectedError`.
- Always use `withTransaction { }` from Database.kt. Never use `transaction { }` (blocking).
```kotlin
class ExposedRequestRepository(
    private val partyRepo: PartyRepository,
) : RequestRepository {

    override suspend fun find(id: UUID): AuthorizationRequest =
        withTransaction {
            AuthorizationRequestTable
                .selectAll()
                .where { AuthorizationRequestTable.id eq id }
                .singleOrNull()
                ?.toAuthorizationRequest()
                ?: throw RepositoryReadError.NotFoundError("Request $id not found")
        }

    override suspend fun insert(request: AuthorizationRequest): AuthorizationRequest =
        withTransaction {
            AuthorizationRequestTable.insert { /* ... */ }
            request
        }
}
```

### Row mapping

```kotlin
fun ResultRow.toAuthorizationRequest(
    requestedBy: AuthorizationPartyRecord,
    // ...
): AuthorizationRequest =
    AuthorizationRequest(
        id = this[AuthorizationRequestTable.id].value,
        type = this[AuthorizationRequestTable.requestType],
        // ...
    )
```

## Repository Exceptions

Reuse the v1 exception types in `v1/repositories/RepositoryError.kt`, which extend the shared `Errors` exception base:

```kotlin
sealed class RepositoryReadError(override val detail: String) : Errors(detail) {
    class NotFoundError(detail: String) : RepositoryReadError(detail)
}

sealed class RepositoryWriteError(override val detail: String) : Errors(detail) {
    class ConflictError(detail: String) : RepositoryWriteError(detail)
    class NotFoundError(detail: String) : RepositoryWriteError(detail)
}
```

Translate exceptions only when the domain meaning changes, preserving the original cause. Do not convert arbitrary exceptions to `UnexpectedError`.

## Atomic Repository Operations

Repositories own transaction boundaries; handlers do not open transactions. Expose one repository operation for each atomic state change,
accepting all data needed for dependent writes. For example, confirming a document must update its status and create its grant and grant properties
in the same transaction, not through independently committed handler calls.

```kotlin
// Repository implementation; write helpers use the enclosing transaction.
override suspend fun confirmAndCreateGrant(
    documentId: UUID,
    grant: NewGrant,
): Grant = withTransaction {
    updateDocumentStatus(documentId)
    val savedGrant = insertGrant(grant)
    insertGrantProperties(savedGrant.id, grant.properties)
    savedGrant
}
```

Both custom and unexpected exceptions must escape the atomic transaction to trigger rollback.
Do not catch a failed dependent write and continue or return success.

Helpers may call `withTransaction` themselves, provided nested blocks participate in the enclosing database transaction. Successful completion of a nested block
must not independently commit dependent writes: rolling back the outer transaction must undo them all. Do not start a separate connection or top-level
transaction for dependent writes. Test failures after earlier writes: if grant or grant-property insertion fails, assert that the document status is unchanged
and no partial grant data remains. Assert the thrown exception as well as persisted state.

## Ktor Native Dependency Injection registration
- Always use Ktor Native Dependency Injection
- ``resolve()`` resolves dependencies through ktor dependency injection registry. Never instantiate the class yourself.
- If a new dependency module is neeeded, it must be appended to application.yml under ktor.application.modules
```kotlin
dependencies {
    provide<ExposedRequestRepository> {
      ExposedRequestRepository(partyRepo = resolve(), requestPropertiesRepository = resolve())
    }
}
```

Always bind the implementation to the interface.

## Liquibase migrations

### Structure

```text
db/
├── db-changelog.yaml   # includeAll in order: schemas → user → types → tables
├── schemas/
├── user/
├── types/              # CREATE TYPE (PostgreSQL enums)
└── tables/             # CREATE TABLE, ALTER TABLE
```

### Adding a migration

1. Find the highest existing sequence number across all directories.
2. Create a `.sql` file in the correct directory.
3. One changeset per file.

```sql
--changeset elhub:31
ALTER TABLE auth.authorization_request
    ADD COLUMN cancelled_at TIMESTAMP WITH TIME ZONE;
```

**Conventions:**

- All objects use `auth.` schema prefix
- Enum types: `CREATE TYPE auth.<name> AS ENUM (...)` in `types/`
- Enum additions: `ALTER TYPE` goes in `tables/`, not `types/`

## Integration tests

```kotlin
class MyIntegrationTest : FunSpec({
    extensions(
        PostgresTestContainerExtension(),               // spins up Postgres, runs all Liquibase migrations
        RunPostgresScriptExtension("db/seed-data.sql")  // seeds test fixtures
    )

    test("persists and retrieves") {
        testApplication { /* full stack */ }
    }
})
```
