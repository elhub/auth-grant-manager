package no.elhub.auth.features.documents.query

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.PartyService
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository

class Handler(
    private val repo: DocumentRepository,
    private val partyService: PartyService
) {
    suspend operator fun invoke(query: Query): Either<QueryError, List<AuthorizationDocument>> = either {
        val requestedByParty = partyService.resolve(query.requestedByIdentifier).mapLeft {
            QueryError.IOError
        }.bind()

        repo.findAll(requestedByParty)
            .mapLeft { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                    is RepositoryReadError.UnexpectedError -> QueryError.IOError
                }
            }.bind()
    }
}
