package no.elhub.auth.features.requests.get

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestRepository
import org.slf4j.LoggerFactory

class Handler(
    private val requestRepository: RequestRepository,
    private val grantRepository: GrantRepository
) {

    private val logger = LoggerFactory.getLogger(Handler::class.java)

    suspend operator fun invoke(query: Query): Either<QueryError, AuthorizationRequest> = either {
        val request = requestRepository.find(query.id)
            .mapLeft { QueryError.ResourceNotFoundError }
            .bind()

        ensure((request.requestedTo == query.authorizedParty) or (request.requestedBy == query.authorizedParty)) {
            logger.error("Requestee is not authorized to get the request ${query.authorizedParty}")
            QueryError.NotAuthorizedError
        }

        // grant can only exist if approvedBy is set
        request.approvedBy?.let {
            val grantsBySourceId = grantRepository.findBySourceIds(
                AuthorizationGrant.SourceType.Request,
                listOf(request.id)
            ).mapLeft {
                logger.error("Failed to fetch grant for request ${request.id}")
                QueryError.IOError
            }.bind()

            val grant = grantsBySourceId[request.id]
            if (grant == null) {
                logger.error("approvedBy is present but grant not found for request ${request.id}")
            }
            grant?.let { request.copy(grantId = it.id) } ?: request
        } ?: request
    }
}
