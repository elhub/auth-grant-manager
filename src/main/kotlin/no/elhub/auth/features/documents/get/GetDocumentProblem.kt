package no.elhub.auth.features.documents.get

sealed class GetDocumentProblem {
    data object NotFoundError : GetDocumentProblem()
    data object IOError : GetDocumentProblem()
}
