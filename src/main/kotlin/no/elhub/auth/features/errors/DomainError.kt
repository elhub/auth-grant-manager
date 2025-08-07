package no.elhub.auth.features.errors

sealed class DomainError {

    sealed class ApiError : DomainError() {
        data object AuthorizationIdIsMalformed : DomainError()
        data object AuthorizationIdIsMissing : DomainError()
        data class AuthorizationPayloadInvalid(val throwable: Throwable) : DomainError()
    }

    sealed class RepositoryError : DomainError() {
        data object AuthorizationNotCreated : DomainError()
        data object AuthorizationNotFound : DomainError()
        data class Unexpected(val exception: Exception) : DomainError()
    }

}
