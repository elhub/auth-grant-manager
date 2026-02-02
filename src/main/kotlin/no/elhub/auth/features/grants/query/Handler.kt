package no.elhub.auth.features.grants.query

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository
import org.jetbrains.exposed.sql.transactions.transaction

class Handler(
    private val repo: GrantRepository,
) {
    operator fun invoke(query: Query): Either<QueryError, List<AuthorizationGrant>> =
        either {
            transaction {
                repo
                    .findAll(query.authorizedParty)
                    .mapLeft {
                        when (it) {
                            RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError
                            RepositoryReadError.UnexpectedError -> QueryError.IOError
                        }
                    }.bind()
            }
        }
}
