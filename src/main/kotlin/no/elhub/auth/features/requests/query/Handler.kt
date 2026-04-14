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

        val enrichedItems = page.items.map { request ->
            if (request.approvedBy == null) {
                request
            } else {
                // grant can only exist if approvedBy is set
                val grant = grantRepository.findBySource(
                    AuthorizationGrant.SourceType.Request,
                    request.id
                ).mapLeft {
                    logger.error("approvedBy is present but grant not found for request ${request.id}")
                    QueryError.ResourceNotFoundError
                }.bind()

                grant?.let { request.copy(grantId = it.id) } ?: request
            }
        }

        page.copy(items = enrichedItems)
    }
}
