package no.elhub.auth.features.requests

sealed class AuthorizationRequestProblem {
    data object NotFoundError : AuthorizationRequestProblem()
    data object DataBaseError : AuthorizationRequestProblem()
    data object UnexpectedError : AuthorizationRequestProblem()
}
