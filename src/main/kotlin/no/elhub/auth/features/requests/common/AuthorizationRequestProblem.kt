package no.elhub.auth.features.requests.common

sealed class AuthorizationRequestProblem {
    data object NotFoundError : AuthorizationRequestProblem()
    data object DataBaseError : AuthorizationRequestProblem()
    data object UnexpectedError : AuthorizationRequestProblem()
}
