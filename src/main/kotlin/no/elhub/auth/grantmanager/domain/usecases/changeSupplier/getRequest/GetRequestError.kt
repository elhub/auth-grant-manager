package no.elhub.auth.grantmanager.domain.usecases.changeSupplier.getRequest

sealed class GetRequestError {
    data object NotFound : GetRequestError()
    data class SystemErrorRequest(val reason: String) : GetRequestError()
}
