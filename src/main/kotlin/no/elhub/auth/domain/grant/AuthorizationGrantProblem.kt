package no.elhub.auth.domain.grant

sealed class AuthorizationGrantProblem {
    data object NotFoundError : AuthorizationGrantProblem()

    data object DataBaseError : AuthorizationGrantProblem()

    data object UnexpectedError : AuthorizationGrantProblem()
}
