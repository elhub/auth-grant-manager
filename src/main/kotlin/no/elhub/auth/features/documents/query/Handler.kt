package no.elhub.auth.features.documents.query

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository

class Handler(
    private val repo: DocumentRepository,
    private val grantRepository: GrantRepository
) {
    operator fun invoke(query: Query): Either<QueryError, List<AuthorizationDocument>> = either {
        val documents = repo.findAll(query.authorizedParty)
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

            document.copy(
                grantId = grant?.id,
            )
        }
    }
}
