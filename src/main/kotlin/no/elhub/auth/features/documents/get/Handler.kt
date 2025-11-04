package no.elhub.auth.features.documents.get

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.AuthorizationParty
import no.elhub.auth.features.common.PartyRepository
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository

class Handler(
    private val documentRepo: DocumentRepository,
) {
    operator fun invoke(query: Query): Either<QueryError, AuthorizationDocument> = either {
        documentRepo.find(query.id)
            .mapLeft { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                    is RepositoryReadError.UnexpectedError -> QueryError.IOError
                }
            }.bind()
    }
}
