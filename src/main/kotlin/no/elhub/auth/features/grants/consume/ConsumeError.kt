package no.elhub.auth.features.grants.consume

sealed class ConsumeError {
    data object GrantNotFound : ConsumeError()
    data object PersistenceError : ConsumeError()
    data object NotAuthorized : ConsumeError()
}
