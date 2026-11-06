---
name: explicit-error-handling
description: >
  Use when writing or reviewing any Handler, Service, or Repository.
  Defines explicit domain error handling for business logic and database access.
  Load before writing or reviewing any Handler, Service, or Repository.
---
# Explicit Error Handling

Use Arrow for expected domain failures that callers distinguish. Unexpected infrastructure failures remain exceptions; an `Either` return type does not promise
exception-free execution. Preserve the existing error model when maintaining exception-based v1 code.

## Error contract

```kotlin
suspend fun findUser(id: UserId): Either<UserNotFound, User>
```

Model meaningful outcomes such as not found, conflict, or expiry. Do not carry a generic 500 through every layer as a typed error.

## Composing operations

```kotlin
either {
    val party = partyService.resolve(model.requestedBy).bind()
    ensure(model.authorizedParty == party) { CreateError.AuthorizationError }
    repo.insert(model.toRequest()).bind()
}
```

Use compatible error types for direct binding. Translate errors only when their meaning changes; reuse repeated translations instead of creating a new hierarchy
per layer.

## List operations

```kotlin
either {
    for (item in items) process(item).bind()
}
```

## Domain error types

Use cohesive sealed error interfaces for expected outcomes:

```kotlin
sealed interface CreateRequestError {
    data object Unauthorized : CreateRequestError
    data object InvalidInput : CreateRequestError
}
```

Sealed interfaces support exhaustive handling. Interpret typed errors at the HTTP boundary; log unexpected exceptions there with their original cause.

## Wrapping Exposed transactions

Catch specific library exceptions only where they represent expected outcomes. Keep conversion outside the transaction so SQL exceptions reach rollback first:

```kotlin
suspend fun insert(user: NewUser): Either<UserAlreadyExists, User> = either {
    try {
        withTransaction { insertUserRow(user) }
    } catch (e: ExposedSQLException) {
        if (isUsernameUniqueViolation(e)) raise(UserAlreadyExists(user.username))
        else throw e
    }
}
```

The constraint classifier above is application-specific: check the SQL state and relevant constraint, not every SQL error. Never swallow cancellation or fatal
errors, discard original causes, or introduce a universal exception-to-Left wrapper.

Repositories own transactions; handlers call a single repository operation with all data needed for an atomic change (for example, document confirmation plus
grant creation). Use the shared suspending `withTransaction`, not blocking `transaction`. Keep `either` outside the atomic transaction boundary so the internal
exception from `raise` or a failing `.bind()` escapes through Exposed, triggering rollback before the outer `either` converts it to `Left`. A normally returned
`Left` does not trigger rollback. Test a write followed by `raise` or a failing `.bind()`: assert no partial persistence and an outer `Left`.
Helpers may use nested `withTransaction` blocks that participate in the enclosing database transaction; outer rollback must undo all dependent writes.
Do not start independent transactions for those writes.

## References

- [Karlo Bratko: instakt](https://github.com/karlobratko/instakt/tree/7084ed472695b69e4ead99bd2511fd81d606df33): see `ExposedUserPersistence.kt` for narrow
  adapters and outer `either`; older dependencies, and its unexpected-error conversion loses the original cause.
- [Arrow: converting exceptions](https://arrow-kt.io/learn/typed-errors/working-with-typed-errors/#from-exceptions).
