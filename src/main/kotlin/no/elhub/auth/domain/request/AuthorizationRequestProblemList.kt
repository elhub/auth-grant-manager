package no.elhub.auth.domain.request

sealed class AuthorizationRequestProblemList {
    data object DataBaseError : AuthorizationRequestProblemList()
    data object UnexpectedError : AuthorizationRequestProblemList()
}
