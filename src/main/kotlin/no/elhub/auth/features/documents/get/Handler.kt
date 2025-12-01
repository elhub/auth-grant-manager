package no.elhub.auth.features.documents.get

import arrow.core.Either
import arrow.core.raise.context.bind
import arrow.core.raise.either
import no.elhub.auth.features.common.PartyService
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository
import java.util.UUID

class Handler(
    private val documentRepo: DocumentRepository,
    private val partyService: PartyService,
    private val grantRepository: GrantRepository
) {
    suspend operator fun invoke(query: Query): Either<QueryError, AuthorizationDocument> = either {
        val document = documentRepo.find(query.documentId)
            .mapLeft { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                    is RepositoryReadError.UnexpectedError -> QueryError.IOError
                }
            }.bind()

        val requestedByParty = partyService.resolve(query.requestedByIdentifier)
            .mapLeft { QueryError.IOError }.bind()

        if (document.requestedBy.resourceId != requestedByParty.resourceId) {
            raise(QueryError.RequestedByMismatch)
        }
        val grant = grantRepository.findBySource(AuthorizationGrant.SourceType.Document, document.id)
            .mapLeft { error ->
                when (error) {
                    RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                    RepositoryReadError.UnexpectedError -> QueryError.IOError
                }
            }.bind()

        grant?.let {
            document.copy(
                grantId = grant.id
            )
        } ?: document
    }
}
