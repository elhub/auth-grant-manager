package no.elhub.auth.v1.repositories

import no.elhub.auth.v1.Errors

sealed class RepositoryWriteError(override val detail: String) : Errors(detail) {
    class ConflictError(detail: String) : RepositoryWriteError(detail)
    class NotFoundError(detail: String) : RepositoryWriteError(detail)
    class ExpiredError(detail: String) : RepositoryWriteError(detail)
    class UnexpectedError(detail: String) : RepositoryWriteError(detail)
}

sealed class RepositoryReadError(override val detail: String) : Errors(detail) {
    class NotFoundError(detail: String) : RepositoryReadError(detail)
    class UnexpectedError(detail: String) : RepositoryReadError(detail)
}
