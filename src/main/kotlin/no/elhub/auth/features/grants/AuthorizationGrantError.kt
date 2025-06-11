package no.elhub.auth.features.grants

sealed class AuthorizationGrantError {
    data object NotFoundError : AuthorizationGrantError()

    data object DataBaseError : AuthorizationGrantError()

    data object InternalServerError : AuthorizationGrantError()

    data object IllegalArgumentError : AuthorizationGrantError()
}
