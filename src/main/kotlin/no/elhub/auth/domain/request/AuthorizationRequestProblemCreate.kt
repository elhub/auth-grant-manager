package no.elhub.auth.domain.request

sealed class AuthorizationRequestProblemCreate {
    data object DataBaseError : AuthorizationRequestProblemCreate()
    data object UnexpectedError : AuthorizationRequestProblemCreate()
}
