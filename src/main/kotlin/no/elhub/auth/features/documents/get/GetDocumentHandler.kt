package no.elhub.auth.features.documents.get

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository

class GetDocumentHandler(
    private val repo: DocumentRepository
) {
    operator fun invoke(query: GetDocumentQuery): Either<QueryError, AuthorizationDocument> =
        repo.find(query.id).fold(
            { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError.left()
                    is RepositoryReadError.UnexpectedError -> QueryError.IOError.left()
                }
            },
            { document -> document.right() }
        )
}
