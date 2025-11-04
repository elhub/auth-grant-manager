package no.elhub.auth.features.documents.get

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.common.AuthorizationParty
import no.elhub.auth.features.common.PartyRepository

data class GetDocumentResult(
    val document: AuthorizationDocument,
    val requestedByParty: AuthorizationParty,
    val requestedFromParty: AuthorizationParty
)

class Handler(
    private val documentRepo: DocumentRepository,
    private val partyRepo: PartyRepository,
) {
    operator fun invoke(query: Query): Either<QueryError, GetDocumentResult> = either {
        val documentToGet = documentRepo.find(query.id)
            .mapLeft { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                    is RepositoryReadError.UnexpectedError -> QueryError.IOError
                }
            }.bind()

        val requestedBy = partyRepo.find(documentToGet.requestedBy)
            .mapLeft { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                    is RepositoryReadError.UnexpectedError -> QueryError.IOError
                }
            }.bind()

        val requestedFrom = partyRepo.find(documentToGet.requestedFrom)
            .mapLeft { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                    is RepositoryReadError.UnexpectedError -> QueryError.IOError
                }
            }.bind()

        GetDocumentResult(
            document = documentToGet,
            requestedByParty = requestedBy,
            requestedFromParty = requestedFrom
        )
    }
}
