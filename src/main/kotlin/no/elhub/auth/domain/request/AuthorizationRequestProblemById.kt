package no.elhub.auth.domain.request

sealed class AuthorizationRequestProblemById {
    data object NotFoundError : AuthorizationRequestProblemById()
    data object DataBaseError : AuthorizationRequestProblemById()
    data object UnexpectedError : AuthorizationRequestProblemById()
}
