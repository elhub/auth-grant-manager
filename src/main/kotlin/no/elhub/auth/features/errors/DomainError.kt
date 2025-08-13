package no.elhub.auth.features.errors

sealed class DomainError

sealed class ApiError : DomainError() {
    data object AuthorizationIdIsMalformed : ApiError()
    data object AuthorizationRequestTypeIsInvalid : ApiError()
    data class AuthorizationPayloadInvalid(val throwable: Throwable) : ApiError()
}

sealed class RepositoryError : DomainError() {
    data object AuthorizationNotCreated : RepositoryError()
    data object AuthorizationNotFound : RepositoryError()
    data object AuthorizationPartyNotFound : RepositoryError()
    data class UnexpectedRepositoryFailure(val throwable: Throwable) : RepositoryError()
}

sealed class UnexpectedError : DomainError() {
    data class UnexpectedFailure(val throwable: Throwable) : UnexpectedError()
}
