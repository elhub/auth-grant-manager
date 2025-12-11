package no.elhub.auth.features.grants.consume

sealed class ConsumeError {
    data object RequestNotFound : ConsumeError()
    data object PersistenceError : ConsumeError()
}
