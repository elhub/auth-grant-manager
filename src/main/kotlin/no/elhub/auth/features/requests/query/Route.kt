package no.elhub.auth.features.requests.query

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.features.common.Pagination
import no.elhub.auth.features.common.auth.authorizedParty
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validateEnumListParam
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.query.dto.toGetCollectionResponse
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(Route::class.java)
private const val STATUS_FILTER_PARAM = "filter[status]"

fun Route.route(handler: Handler) {
    get {
        val pagination = Pagination.from(
            pageParam = call.request.queryParameters["page[number]"],
            sizeParam = call.request.queryParameters["page[size]"],
        )

        val statuses = validateEnumListParam<AuthorizationRequest.Status>(
            call.request.queryParameters[STATUS_FILTER_PARAM],
            STATUS_FILTER_PARAM
        )
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        val query = Query(authorizedParty = call.authorizedParty, pagination = pagination, statuses = statuses)

        val page = handler(query)
            .getOrElse { error ->
                logger.error("Failed to get authorization requests: {}", error)
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }
        call.respond(HttpStatusCode.OK, page.toGetCollectionResponse(statuses))
    }
}
