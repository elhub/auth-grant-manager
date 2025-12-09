package no.elhub.auth.features.requests.query

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestRepository

class Handler(private val repo: RequestRepository) {
    operator fun invoke(query: Query): Either<QueryError, List<AuthorizationRequest>> =
        repo.findAllRequests()
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
