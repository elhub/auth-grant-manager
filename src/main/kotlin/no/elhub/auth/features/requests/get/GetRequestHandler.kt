package no.elhub.auth.features.requests.get

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.RepositoryReadError
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestRepository

class GetRequestHandler(private val repo: RequestRepository) {
    operator fun invoke(query: GetRequestQuery): Either<QueryError, AuthorizationRequest> =
        repo.find(query.id).fold(
            { error ->
                when (error) {
                    is RepositoryReadError.NotFoundError -> QueryError.ResourceNotFoundError.left()
                    is RepositoryReadError.UnexpectedError -> QueryError.IOError.left()
                }
            },
            { request -> request.right() }
        )
}
