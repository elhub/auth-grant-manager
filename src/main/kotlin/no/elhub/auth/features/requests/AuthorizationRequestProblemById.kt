package no.elhub.auth.features.requests

sealed class AuthorizationRequestProblemById {
    data object NotFoundError : AuthorizationRequestProblemById()
    data object DataBaseError : AuthorizationRequestProblemById()
    data object UnexpectedError : AuthorizationRequestProblemById()
}
