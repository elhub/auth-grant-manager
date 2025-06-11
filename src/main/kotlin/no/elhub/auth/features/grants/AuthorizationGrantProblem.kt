package no.elhub.auth.features.grants

sealed class AuthorizationGrantProblem {
    data object NotFoundError : AuthorizationGrantProblem()

    data object DataBaseError : AuthorizationGrantProblem()

    data object InternalServerError : AuthorizationGrantProblem()

    data object NullPointerError : AuthorizationGrantProblem()

    data object IllegalArgumentError : AuthorizationGrantProblem()

    data object UnknownError : AuthorizationGrantProblem()
}
