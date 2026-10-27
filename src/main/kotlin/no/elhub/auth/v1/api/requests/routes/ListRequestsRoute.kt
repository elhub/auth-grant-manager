package no.elhub.auth.v1.api.requests.routes

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.auth.authorizedParty
import no.elhub.auth.v0.features.common.toApiErrorResponse
import no.elhub.auth.v0.features.common.validateEnumListParam
import no.elhub.auth.v1.api.requests.response.toGetCollectionResponse
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.features.requests.ListRequestsHandler

private const val STATUS_FILTER_PARAM = "filter[status]"

fun Route.route(handler: ListRequestsHandler) {
    get {
        val pagination = Pagination.from(
            pageParam = call.request.queryParameters["page[number]"],
            sizeParam = call.request.queryParameters["page[size]"],
        )
        val statuses = validateEnumListParam<AuthorizationRequestStatus>(
            call.request.queryParameters[STATUS_FILTER_PARAM],
            STATUS_FILTER_PARAM,
        ).getOrElse { error ->
            val (status, body) = error.toApiErrorResponse()
            call.respond(status, body)
            return@get
        }

        val page = handler(call.authorizedParty, pagination, statuses)
        call.respond(HttpStatusCode.OK, page.toGetCollectionResponse(statuses))
    }
}
