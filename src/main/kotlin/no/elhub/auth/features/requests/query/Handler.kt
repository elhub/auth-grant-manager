package no.elhub.auth.features.requests.query

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.Page
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestRepository
import org.slf4j.LoggerFactory

class Handler(
    private val requestRepository: RequestRepository,
    private val grantRepository: GrantRepository,
) {

    private val logger = LoggerFactory.getLogger(Handler::class.java)

    suspend operator fun invoke(query: Query): Either<QueryError, Page<AuthorizationRequest>> = either {
        val page = requestRepository.findAllAndSortByCreatedAt(query.authorizedParty, query.pagination)
            .mapLeft { QueryError.ResourceNotFoundError }
            .bind()

        val approvedRequestIds = page.items.mapNotNull { request ->
            request.id.takeIf { request.approvedBy != null }
        }

        val grantsBySourceId = grantRepository.findBySourceIds(
            AuthorizationGrant.SourceType.Request,
            approvedRequestIds
        ).mapLeft {
            logger.error("Failed to batch-fetch grants for approved requests")
            QueryError.IOError
        }.bind()

        val enrichedItems = page.items.map { request ->
            if (request.approvedBy == null) {
                request
            } else {
                val grant = grantsBySourceId[request.id]
                if (grant == null) {
                    logger.error("approvedBy is present but grant not found for request ${request.id}")
                }
                grant?.let { request.copy(grantId = it.id) } ?: request
            }
        }

        page.copy(items = enrichedItems)
    }
}
