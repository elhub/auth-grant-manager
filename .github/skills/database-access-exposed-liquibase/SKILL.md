---
name: database-access-exposed-liquibase
description: >
  Use when writing or reviewing any Repository, Table object, or migration.
  Defines Exposed table conventions, the Either-returning repository pattern,
  withTransaction usage, and Liquibase migration rules.
  Load before generating any database access code.
---
# Database Access with Exposed & Liquibase

## Table definitions

`object` inside the owning feature's `common/`. All names schema-qualified `"auth.<name>"`.

**UUID PK entities → `UUIDTable`.** Never `Table` + `uuid("id")` — that maps to Kotlin's experimental `kotlin.uuid.Uuid`, not `java.util.UUID`.
**Join tables → `Table`** with explicit `PrimaryKey`.

```kotlin
object AuthorizationRequestTable : UUIDTable("auth.authorization_request") {
    val requestType = customEnumeration("request_type", "auth.authorization_request_type",
        fromDb = { AuthorizationRequest.Type.valueOf(it as String) },
        toDb = { PGEnum("authorization_request_type", it) })
    val requestedBy = javaUUID("requested_by").references(AuthorizationPartyTable.id)
    val createdAt = timestampWithTimeZone("created_at").clientDefault { currentTimeUtc() }
}

// Join table
object RequestScopeTable : Table("auth.authorization_request_scope") {
    val requestId = javaUUID("request_id").references(AuthorizationRequestTable.id, onDelete = ReferenceOption.CASCADE)
    val scopeId   = javaUUID("scope_id").references(AuthorizationScopeTable.id, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(requestId, scopeId)
}
```

Conventions: PostgreSQL enums → `customEnumeration` + `PGEnum`. Timestamps → `timestampWithTimeZone`. FKs → `.references(OtherTable.id)`.

## Repository pattern

Interface in `features/{domain}/common/`. Impl as `ExposedXxxRepository`. All functions `suspend`. Return `Either<RepositoryError, T>`.

```kotlin
interface RequestRepository {
    suspend fun find(id: UUID): Either<RepositoryReadError, AuthorizationRequest>
    suspend fun insert(request: AuthorizationRequest): Either<RepositoryWriteError, AuthorizationRequest>
}

class ExposedRequestRepository : RequestRepository {

    override suspend fun find(id: UUID): Either<RepositoryReadError, AuthorizationRequest> = either {
        Either.catch {
            withTransaction {
                AuthorizationRequestTable.selectAll()
                    .where { AuthorizationRequestTable.id eq id }
                    .singleOrNull()
            }
        }
        .mapLeft { RepositoryReadError.UnexpectedError }
        .bind() ?: raise(RepositoryReadError.NotFoundError)  // use raise(), NOT .flatMap() — doesn't exist
    }

    override suspend fun insert(request: AuthorizationRequest): Either<RepositoryWriteError, AuthorizationRequest> =
        Either.catch {
            withTransaction { AuthorizationRequestTable.insert { /* ... */ }; request }
        }.mapLeft { RepositoryWriteError.UnexpectedError }
}
```

Row mapping: `fun ResultRow.toXxx(): Xxx = Xxx(id = this[Table.id].value, ...)`.

## Error types (`features/common/Errors.kt`)

```kotlin
sealed class RepositoryReadError  { data object NotFoundError, UnexpectedError }
sealed class RepositoryWriteError { data object ConflictError, NotFoundError, UnexpectedError }
```

Handlers map to their own error type via `mapLeft` before `bind()`.

## Transactions

- Always `withTransaction { }` from `config/Database.kt`. Never `transaction { }` (blocking).
- `withTransaction` reuses an open transaction — double-wrapping (handler + repository) is safe.
- Use handler-level `withTransaction` when atomicity spans multiple repository calls.

```kotlin
// Handler — atomic multi-step
withTransaction {
    repo.insert(entity).mapLeft { CreateError.PersistenceError }.bind()
    otherRepo.insert(related).mapLeft { CreateError.PersistenceError }.bind()
}
```

## update() syntax

`update` is a top-level extension — always use named `where =` parameter:

```kotlin
import org.jetbrains.exposed.v1.jdbc.update

val rows = MyTable.update(where = { MyTable.id eq id }) {
    it[MyTable.status] = newStatus
}
if (rows == 0) raise(RepositoryWriteError.NotFoundError)
```

**Never** `MyTable.update({ condition }) { }` — positional lambda overload does not exist in Exposed v1.

## Repository integration tests

```kotlin
class ExposedFooRepositoryTest : FunSpec({
    extension(PostgresTestContainerExtension())

    val repo = ExposedFooRepository()

    beforeSpec {
        Database.connect(PostgresTestContainer.JDBC_URL, PostgresTestContainer.DRIVER,
            PostgresTestContainer.USERNAME, PostgresTestContainer.PASSWORD)
    }

    beforeTest { transaction { FooTable.deleteAll() } }
    // deleteAll import: org.jetbrains.exposed.v1.jdbc.deleteAll
})
```

## Liquibase migrations

```text
db/
├── db-changelog.yaml   # includeAll: schemas → user → types → tables
├── types/              # CREATE TYPE auth.<name> AS ENUM (...)
└── tables/             # CREATE TABLE, ALTER TABLE, ALTER TYPE
```

1. Find highest sequence number across all directories.
2. Create `<next>-<description>.sql` in the correct directory.
3. One changeset per file: `--changeset elhub:<N>`

All objects use `auth.` schema prefix. Enum additions (ALTER TYPE) go in `tables/`, not `types/`.
