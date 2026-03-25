---
name: database-access-exposed-liquibase
description: Use when writing or reviewing any Repository, Table object, or migration. Defines Exposed table conventions, the Either-returning repository pattern, newSuspendedTransaction usage, and Liquibase migration rules. Load before generating any database access code.
---

# Database Access with Exposed & Liquibase

Repositories return `Either`. Transactions are explicit. Migrations are versioned sequential SQL files.

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
-
### Interface (in `features/{domain}/common/`)

```kotlin
interface RequestRepository {
    suspend fun find(id: UUID): Either<RepositoryReadError, AuthorizationRequest>
    suspend fun insert(request: AuthorizationRequest): Either<RepositoryWriteError, AuthorizationRequest>
}
```

### Implementation
- DAO always happens through org.jetbrains.exposed, do not use SQL queries. Table must have a table type defined through exposed.
- Always use Either to map out the Result / Error. E.g. Either<Error, BusinessObject>
- All errors in repository must be handled and defined as a RepositoryError. This can be found in Errors.kt.
-
```kotlin
class ExposedRequestRepository(
    private val partyRepo: PartyRepository,
) : RequestRepository {

    override suspend fun find(id: UUID): Either<RepositoryReadError, AuthorizationRequest> =
        Either.catch {
            newSuspendedTransaction {
                AuthorizationRequestTable
                    .selectAll()
                    .where { AuthorizationRequestTable.id eq id }
                    .singleOrNull()
            }
        }
            .mapLeft { RepositoryReadError.UnexpectedError }
            .flatMap { row -> row?.toAuthorizationRequest() ?: RepositoryReadError.NotFoundError.left() }

    override suspend fun insert(request: AuthorizationRequest): Either<RepositoryWriteError, AuthorizationRequest> =
        Either.catch {
            newSuspendedTransaction {
                AuthorizationRequestTable.insert { /* ... */ }
                request
            }
        }.mapLeft { RepositoryWriteError.UnexpectedError }
}
```

Always use `newSuspendedTransaction { }`. Never use `transaction { }` (blocking).

### Row mapping

```kotlin
fun ResultRow.toAuthorizationRequest(
    requestedBy: AuthorizationPartyRecord,
    // ...
): Either<RepositoryReadError, AuthorizationRequest> = Either.catch {
    AuthorizationRequest(
        id = this[AuthorizationRequestTable.id].value,
        type = this[AuthorizationRequestTable.requestType],
        // ...
    )
}.mapLeft { RepositoryReadError.UnexpectedError }
```

## Error types (from `features/common/Errors.kt`)

```kotlin
sealed class RepositoryReadError : RepositoryError() {
    data object NotFoundError : RepositoryReadError()
    data object UnexpectedError : RepositoryReadError()
}

sealed class RepositoryWriteError : RepositoryError() {
    data object ConflictError : RepositoryWriteError()
    data object NotFoundError : RepositoryWriteError()
    data object UnexpectedError : RepositoryWriteError()
}
```

Handlers map these to their own feature error type via `mapLeft` before `bind()`.

## Transactions in Handlers

Wrap multi-step repository calls in a single `newSuspendedTransaction` when atomicity is required:

```kotlin
val result = newSuspendedTransaction {
    repo.insert(entity).mapLeft { CreateError.PersistenceError }.bind()
}
```

Handlers own transaction boundaries. Repositories do not nest transactions.

## Koin registration

```kotlin
koinModule {
    singleOf(::ExposedRequestRepository) bind RequestRepository::class
}
```

Always bind the implementation to the interface.

## Liquibase migrations

### Structure

```
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
