package no.elhub.auth.features.requests.update

sealed class UpdateError {
    data object NotAuthorizedError : UpdateError()
    data object RequestNotFound : UpdateError()
    data object PersistenceError : UpdateError()
    data object ScopeReadError : UpdateError()
    data object GrantCreationError : UpdateError()
    data object IllegalTransitionError : UpdateError()
    data object IllegalStateError : UpdateError()
}
