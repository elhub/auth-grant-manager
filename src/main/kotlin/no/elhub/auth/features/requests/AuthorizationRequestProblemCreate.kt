package no.elhub.auth.features.requests

sealed class AuthorizationRequestProblemCreate {
    data object DataBaseError : AuthorizationRequestProblemCreate()
    data object UnexpectedError : AuthorizationRequestProblemCreate()
}
