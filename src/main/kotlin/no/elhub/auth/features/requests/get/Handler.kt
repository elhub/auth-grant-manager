package no.elhub.auth.features.requests.get

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestRepository
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

class Handler(
    private val requestRepository: RequestRepository,
    private val grantRepository: GrantRepository
) {

    private val logger = LoggerFactory.getLogger(Handler::class.java)

    operator fun invoke(query: Query): Either<QueryError, AuthorizationRequest> = either {
        val requestWithGrant = transaction {
            val request = requestRepository.find(query.id)
                .mapLeft { QueryError.ResourceNotFoundError }
                .bind()

            ensure((request.requestedTo == query.authorizedParty) or (request.requestedBy == query.authorizedParty)) {
                logger.error("Requestee is not authorized to get the request ${query.authorizedParty}")
                QueryError.NotAuthorizedError
            }

            // grant can only exist if approvedBy is set
            request.approvedBy?.let {
                val grant = grantRepository.findBySource(
                    AuthorizationGrant.SourceType.Request,
                    request.id
                ).mapLeft {
                    logger.error("approvedBy is present but grant not found for request ${request.id}")
                    QueryError.ResourceNotFoundError
                }.bind()

                grant?.let { request.copy(grantId = it.id) } ?: request
            } ?: request
        }

        requestWithGrant
    }
}
