package no.elhub.auth.features.requests.get

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestRepository

class Handler(
    private val requestRepository: RequestRepository,
    private val grantRepository: GrantRepository
) {
    operator fun invoke(query: Query): Either<QueryError, AuthorizationRequest> = either {
        val request = requestRepository.find(query.id)
            .mapLeft { QueryError.ResourceNotFoundError }
            .bind()

        // grant can only exist if approvedBy is set
        val requestWithGrant = request.approvedBy?.let {
            val grant = grantRepository.findBySource(
                AuthorizationGrant.SourceType.Request,
                request.id
            )
                .mapLeft { QueryError.ResourceNotFoundError }
                .bind()

            grant?.let { request.copy(grantId = it.id) } ?: request
        } ?: request

        requestWithGrant
    }
}
