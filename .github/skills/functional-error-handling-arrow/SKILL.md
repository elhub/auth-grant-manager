---
name: explicit-error-handling
description: >
  Use when writing or reviewing any Handler, Service, or Repository.
  Defines explicit domain error handling for business logic and database access.
  Load before writing or reviewing any Handler, Service, or Repository.
---
# Explicit Error Handling

Expected domain failures are represented explicitly. Exceptions are never used for expected domain errors.

## Error contract

```kotlin
// ✅ Return a result that makes success and expected failure explicit.
suspend fun findUser(id: UserId): UserResult

// ❌ — nullable loses error context
suspend fun findUser(id: UserId): User?

// ❌ — throws on failure
suspend fun findUser(id: UserId): User
```

This applies to every Handler, Service, and Repository in the codebase.

## Composing operations

```kotlin
val party = partyService.resolve(model.requestedBy)
    ?: return CreateError.PartyResolutionFailed

if (model.authorizedParty != party) {
    return CreateError.AuthorizationError
}

return repo.insert(model.toRequest())
    ?: CreateError.PersistenceError
```

Map dependency failures to the caller's domain error type at the boundary. Keep the caller's error type explicit.

## List operations

```kotlin
// Fail fast — stop on the first error.
for (item in items) {
    process(item) ?: return ValidationError.InvalidItem
}
```

## Domain error types

Errors are sealed interfaces, one per action slice:

```kotlin
sealed interface CreateRequestError {
    data object Unauthorized : CreateRequestError
    data object InvalidInput : CreateRequestError
    data class PersistenceError(val cause: RepositoryWriteError) : CreateRequestError
}
```

Sealed interfaces enforce exhaustive `when` handling at call sites. Do not use `Exception` subclasses.

## Wrapping Exposed transactions

Database calls can throw. Wrap them:

```kotlin
suspend fun insert(entity: Entity): RepositoryResult {
    return try {
        withTransaction { /* Exposed DSL */ }
        RepositoryResult.Success(entity)
    } catch (exception: Exception) {
        RepositoryResult.Failure(RepositoryWriteError.UnexpectedError)
    }
}
```

Never call `transaction { }` (blocking). Use `withTransaction { }` from ``Database.kt``.

## Hard rules

| Situation                       | Rule                                                      |
|---------------------------------|-----------------------------------------------------------|
| Domain or expected errors       | Return an explicit error, never throw                    |
| Dependency errors               | Map to the caller's domain error type                    |
| Exception from infrastructure   | Catch and map to a repository error                      |
| Truly unrecoverable failure     | Allowed to throw (e.g., OOM, misconfiguration at startup) |
