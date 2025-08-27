package no.elhub.auth.features.documents.get

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.DocumentRepositoryProblem

class GetDocumentHandler(
    private val repo: DocumentRepository
) {
    operator fun invoke(query: GetDocumentQuery): Either<GetDocumentProblem, AuthorizationDocument> =
        repo.find(query.id).fold(
            ifLeft = { error ->
                when (error) {
                    is DocumentRepositoryProblem.NotFoundError -> GetDocumentProblem.NotFoundError.left()
                    is DocumentRepositoryProblem.UnexpectedError -> GetDocumentProblem.IOError.left()
                }
            },
            ifRight = { document -> document.right() }
        )
}
