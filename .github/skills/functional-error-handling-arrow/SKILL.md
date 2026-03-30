---
name: functional-error-handling-arrow
description: >
  Use when writing or reviewing any Handler, Service, or Repository.
  Defines how Either, bind(), mapLeft, ensure, and Either.catch are used in this codebase.
  Load before writing any business logic or database access code.
---
# Functional Error Handling with Arrow

All business logic returns `Either<Failure, Success>`. No exceptions for domain errors.

## Composing with `either { }` and `bind()`

```kotlin
suspend fun createRequest(model: CreateRequestModel): Either<CreateError, AuthorizationRequest> = either {
    val party = partyService.resolve(model.requestedBy)
        .mapLeft { CreateError.PartyResolutionFailed }
        .bind()                                           // unwraps Right or short-circuits Left

    ensure(model.authorizedParty == party) { CreateError.AuthorizationError }  // short-circuits if false

    repo.insert(model.toRequest())
        .mapLeft { CreateError.PersistenceError }
        .bind()
}
```

- `bind()` is the only way to unwrap inside `either { }`. Never `getOrThrow()` or `!!`.
- `mapLeft` maps the dependency's error to the caller's sealed error before `bind()`.
- **Never use `.flatMap { }` on `Either`** — it does not exist in this Arrow version.

## Domain error types

One sealed interface per action slice:

```kotlin
sealed interface CreateRequestError {
    data object Unauthorized : CreateRequestError
    data object InvalidInput : CreateRequestError
    data class PersistenceError(val cause: RepositoryWriteError) : CreateRequestError
}
```

## Wrapping infrastructure

```kotlin
Either.catch { withTransaction { /* Exposed DSL */ } }
    .mapLeft { RepositoryWriteError.UnexpectedError }
```

## List operations

```kotlin
items.map { it.bind() }              // fail-fast — stop on first error
items.mapOrAccumulate { it.bind() }  // accumulate all errors
```

## Hard rules

| Situation | Rule |
| --------- | ---- |
| Domain/expected errors | Return `Left`, never throw |
| Unwrap inside `either { }` | `bind()` only |
| Unwrap outside `either { }` | `fold`, `getOrElse`, `map` — never `getOrThrow()` |
| Infrastructure exception | `Either.catch { }` then `mapLeft` |
| Truly unrecoverable | Allowed to throw (OOM, startup misconfiguration) |
