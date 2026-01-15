package no.elhub.auth.features.grants.consume

sealed class ConsumeError {
    data object GrantNotFound : ConsumeError()
    data object PersistenceError : ConsumeError()
    data object NotAuthorized : ConsumeError()
    data object ExpiredError : ConsumeError()
    data object IllegalTransitionError : ConsumeError()
    data object IllegalStateError : ConsumeError()
}
