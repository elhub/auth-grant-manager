package no.elhub.auth.features.errors

sealed class DomainError {

    sealed class ApiError : DomainError() {
        data object AuthorizationIdIsMalformed : ApiError()
        data object AuthorizationRequestTypeIsInvalid : ApiError()
        data class AuthorizationPayloadInvalid(val throwable: Throwable) : ApiError()
    }

    sealed class RepositoryError : DomainError() {
        data object AuthorizationNotCreated : RepositoryError()
        data object AuthorizationNotFound : RepositoryError()
        data class Unexpected(val exception: Exception) : RepositoryError()
    }
}
