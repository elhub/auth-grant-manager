package no.elhub.auth.features.requests.confirm

sealed class ConfirmError {
    data object RequestNotFound : ConfirmError()
    data object PersistenceError : ConfirmError()
}
