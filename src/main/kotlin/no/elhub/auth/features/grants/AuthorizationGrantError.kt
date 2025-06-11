package no.elhub.auth.features.grants

sealed class AuthorizationGrantError {
    data object NotFound : AuthorizationGrantError()

    data object DataBase : AuthorizationGrantError()

    data object InternalServer : AuthorizationGrantError()

    data object IllegalArgument : AuthorizationGrantError()
}
