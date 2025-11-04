package no.elhub.auth.features.documents.query

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import no.elhub.auth.features.common.AuthorizationParty
import no.elhub.auth.features.common.PartyRepository
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository

class Handler(
    private val repo: DocumentRepository
) {
    operator fun invoke(query: Query): Either<QueryError, List<AuthorizationDocument>> =
        repo.findAll()
            .fold(
                { error ->
                    when (error) {
                        is RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError.left()
                        is RepositoryReadError.UnexpectedError -> QueryError.IOError.left()
                    }
                },
                { documents -> documents.right() }
            )
}
