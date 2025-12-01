package no.elhub.auth.features.documents.query

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.PartyService
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository

class Handler(
    private val repo: DocumentRepository,
    private val partyService: PartyService,
    private val grantRepository: GrantRepository
) {
    suspend operator fun invoke(query: Query): Either<QueryError, List<AuthorizationDocument>> = either {
        val requestedByParty = partyService.resolve(query.requestedByIdentifier).mapLeft {
            QueryError.IOError
        }.bind()

        val documents = repo.findAll(requestedByParty)
            .mapLeft { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                    is RepositoryReadError.UnexpectedError -> QueryError.IOError
                }
            }.bind()

        documents.map { document ->
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
}
