---
name: explicit-error-handling
description: >
  Use when writing or reviewing any Handler, Service, or Repository.
  Defines custom exception handling for business logic and database access.
  Load before writing or reviewing any Handler, Service, or Repository.
---
# Explicit Error Handling

Use custom exceptions for expected domain and repository failures. Return ordinary success values, not Arrow `Either` or success/error wrappers.
Follow the existing v1 exception hierarchy. Maintaining legacy Arrow code does not require migrating unrelated code.

## Error Contracts

Document expected exceptions on public service and repository contracts; Kotlin signatures do not enforce thrown exception types.

```kotlin
/** @throws RepositoryReadError.NotFoundError when the user does not exist. */
suspend fun findUser(id: UserId): User
```

Model meaningful failures such as invalid input, unauthorized access, not found, conflict, and expiry with custom exception classes.
Reuse existing exception types when their meaning fits. Do not invent a hierarchy per function or wrap the same failure at every layer.

## Composing Operations

```kotlin
suspend operator fun invoke(model: CreateRequestModel): AuthorizationRequest {
    val party = partyService.resolve(model.requestedBy)
    if (model.authorizedParty != party) throw CreateError.AuthorizationError()
    return repo.insert(model.toRequest())
}
```

Let exceptions propagate to stop the workflow. Catch only to recover, add meaningful domain context, or translate a known library failure.
Define exception classes, not singleton exception objects. Preserve the original cause when wrapping an exception.

## Library Boundaries

Translate specific library failures only when they represent a known domain outcome. Let unexpected infrastructure exceptions propagate unchanged.

```kotlin
suspend fun insert(user: NewUser): User = try {
    withTransaction { insertUserRow(user) }
} catch (e: ExposedSQLException) {
    if (isUsernameUniqueViolation(e)) throw UserAlreadyExists(user.username, e)
    else throw e
}

class UserAlreadyExists(username: String, cause: Throwable) :
    RuntimeException("Username already exists: $username", cause)
```

The classifier above is application-specific: check the SQL state and relevant constraint, not every SQL error.
Never swallow cancellation or fatal errors. Avoid broad catches that replace every failure with a generic exception or discard the original cause.

## HTTP Boundary

Map custom exceptions to JSON:API responses through the application's centralized Ktor error handling. Add mappings and tests for new exception types.
Unexpected exceptions are logged with their stack trace and return a generic 500 response without exposing internal details.
Handlers and repositories must not import Ktor types or build HTTP responses.

## Atomic Repository Operations

Repositories own transactions; handlers call one repository operation with all data needed for an atomic change.
For example, document confirmation, grant insertion, and grant-property insertion belong in the same repository transaction.
Use the shared suspending `withTransaction`, not blocking `transaction`. Both custom and unexpected exceptions must escape the transaction to trigger rollback.
Do not catch a failed dependent write inside the atomic transaction and continue as if it succeeded.

Helpers may use nested `withTransaction` blocks that participate in the enclosing database transaction; outer rollback must undo all dependent writes.
Do not start independent transactions for those writes. Test a failure after earlier writes: assert the exception and absence of partial persistence.
