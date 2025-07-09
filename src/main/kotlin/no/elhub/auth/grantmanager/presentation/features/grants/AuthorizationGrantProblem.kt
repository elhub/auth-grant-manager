package no.elhub.auth.grantmanager.presentation.features.grants

sealed class AuthorizationGrantProblem {
    data object NotFoundError : AuthorizationGrantProblem()

    data object DataBaseError : AuthorizationGrantProblem()

    data object UnexpectedError : AuthorizationGrantProblem()
}
