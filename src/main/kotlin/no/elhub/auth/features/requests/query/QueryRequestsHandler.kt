package no.elhub.auth.features.requests.query

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestRepository
import java.util.UUID

class QueryRequestsHandler(private val repo: RequestRepository) {
    operator fun invoke(query: QueryRequestsQuery): Either<QueryError, List<AuthorizationRequest>> =
        repo.findAll()
            .fold(
                { error ->
                    when (error) {
                        is RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError.left()
                        is RepositoryReadError.UnexpectedError -> QueryError.IOError.left()
                    }
                },
                { requests -> requests.right() },
            )

}
