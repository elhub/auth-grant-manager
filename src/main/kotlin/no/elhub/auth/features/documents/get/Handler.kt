package no.elhub.auth.features.documents.get

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository
import org.jetbrains.exposed.sql.transactions.transaction

class Handler(
    private val documentRepo: DocumentRepository,
    private val grantRepository: GrantRepository
) {
    operator fun invoke(query: Query): Either<QueryError, AuthorizationDocument> = either {
        transaction {
            val document = documentRepo.find(query.documentId)
                .mapLeft { error ->
                    when (error) {
                        is RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                        is RepositoryReadError.UnexpectedError -> QueryError.IOError
                    }
                }.bind()

            ensure(
                query.authorizedParty == document.requestedBy ||
                    query.authorizedParty == document.requestedFrom
            ) {
                QueryError.NotAuthorizedError
            }

            val grant =
                grantRepository.findBySource(AuthorizationGrant.SourceType.Document, document.id)
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
