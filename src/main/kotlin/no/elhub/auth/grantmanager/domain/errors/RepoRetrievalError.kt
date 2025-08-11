package no.elhub.auth.grantmanager.domain.errors

sealed class RepoRetrievalError {
    data object NotFound : RepoRetrievalError()
    data class SystemError(val reason: String) : RepoRetrievalError()
}
