package no.elhub.auth.v0.features.documents.query

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.QueryError
import no.elhub.auth.v0.features.common.RepositoryReadError
import no.elhub.auth.v0.features.documents.AuthorizationDocument
import no.elhub.auth.v0.features.documents.common.DocumentRepository
import no.elhub.auth.v0.features.grants.AuthorizationGrant
import no.elhub.auth.v0.features.grants.common.GrantRepository

class Handler(
    private val repo: DocumentRepository,
    private val grantRepository: GrantRepository
) {
    suspend operator fun invoke(query: Query): Either<QueryError, Page<AuthorizationDocument>> = either {
        val page = repo.findAndSortByCreatedAt(query.authorizedParty, query.pagination, query.statuses)
            .mapLeft { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                    is RepositoryReadError.UnexpectedError -> QueryError.IOError
                }
            }.bind()

        val documentIds = page.items.map { it.id }
        val grantsBySourceId = grantRepository.findBySourceIds(AuthorizationGrant.SourceType.Document, documentIds)
            .mapLeft { error ->
                when (error) {
                    RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                    RepositoryReadError.UnexpectedError -> QueryError.IOError
                }
            }.bind()

        page.copy(
            items = page.items.map { document ->
                document.copy(grantId = grantsBySourceId[document.id]?.id)
            }
        )
    }
}
