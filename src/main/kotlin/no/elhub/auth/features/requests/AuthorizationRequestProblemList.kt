package no.elhub.auth.features.requests

sealed class AuthorizationRequestProblemList {
    data object DataBaseError : AuthorizationRequestProblemList()
    data object UnexpectedError : AuthorizationRequestProblemList()
}
