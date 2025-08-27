package no.elhub.auth.features.documents.common

sealed class DocumentRepositoryProblem {
    data object NotFoundError : DocumentRepositoryProblem()
    data object UnexpectedError : DocumentRepositoryProblem()
}
