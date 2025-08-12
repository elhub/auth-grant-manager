package no.elhub.auth.domain.request

sealed class AuthorizationRequestProblem {
    data object NotFoundError : AuthorizationRequestProblem()
    data object DataBaseError : AuthorizationRequestProblem()
    data object UnexpectedError : AuthorizationRequestProblem()
}
