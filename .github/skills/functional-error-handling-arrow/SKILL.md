---
name: functional-error-handling-arrow
description: >
  Use when writing or reviewing any Handler, Service, or Repository.
  Defines how Either, bind(), mapLeft, ensure, and Either.catch are used in this codebase.
  Load before writing any business logic or database access code.
---
# Functional Error Handling with Arrow

All business logic returns `Either<Failure, Success>`. Exceptions are never used for domain errors.

## The `Either` contract

```kotlin
// ✅
suspend fun findUser(id: UserId): Either<UserError, User>

// ❌ — nullable loses error context
suspend fun findUser(id: UserId): User?

// ❌ — throws on failure
suspend fun findUser(id: UserId): User
```

This applies to every Handler, Service, and Repository in the codebase.

## Composing with `either { }` and `bind()`

```kotlin
suspend fun createRequest(model: CreateRequestModel): Either<CreateError, AuthorizationRequest> = either {
    // bind() unwraps Right or short-circuits with Left
    val party = partyService.resolve(model.requestedBy)
        .mapLeft { CreateError.PartyResolutionFailed }
        .bind()

    // ensure() short-circuits if condition is false
    ensure(model.authorizedParty == party) { CreateError.AuthorizationError }

    repo.insert(model.toRequest())
        .mapLeft { CreateError.PersistenceError }
        .bind()
}
```

`bind()` is the only way to unwrap inside `either { }`. Never use `getOrThrow()` or `!!`.

## Mapping errors across boundaries (`mapLeft`)

Dependencies return their own error types. Map them before binding:

```kotlin
repository.getData()
    .mapLeft { DomainError.InfrastructureError(it) }
    .bind()
```

The caller's sealed error type is the only error type that escapes the function boundary.

## List operations

```kotlin
// Fail fast — stop on first error
val results = items.map { item -> process(item).bind() }

// Accumulate all errors — use for validation
val results = items.mapOrAccumulate { item -> process(item).bind() }
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
suspend fun insert(entity: Entity): Either<RepositoryWriteError, Entity> =
    Either.catch {
        withTranaction { /* Exposed DSL */ }
    }.mapLeft { RepositoryWriteError.UnexpectedError }
```

Never call `transaction { }` (blocking). Use `withTransaction { }` from ``Database.kt``.

## Hard rules

| Situation                       | Rule                                                      |
|---------------------------------|-----------------------------------------------------------|
| Domain or expected errors       | Return `Left`, never throw                                |
| Unwrapping inside `either { }`  | `bind()` only                                             |
| Unwrapping outside `either { }` | `fold`, `getOrElse`, or `map` — never `getOrThrow()`      |
| Exception from infrastructure   | `Either.catch { }` then `mapLeft`                         |
| Truly unrecoverable failure     | Allowed to throw (e.g., OOM, misconfiguration at startup) |
