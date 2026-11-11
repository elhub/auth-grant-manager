package no.elhub.auth.v1.api.requests.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.api.common.PAGE_NUMBER_PARAM
import no.elhub.auth.v1.api.common.PAGE_SIZE_PARAM
import no.elhub.auth.v1.api.common.STATUS_FILTER_PARAM
import no.elhub.auth.v1.api.common.validateEnumListParam
import no.elhub.auth.v1.api.common.validateQueryParameters
import no.elhub.auth.v1.api.requests.response.toGetCollectionResponse
import no.elhub.auth.v1.common.auth.authorizedAgent
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.features.requests.ListRequestsHandler

fun Route.route(handler: ListRequestsHandler) {
    get {
        call.validateQueryParameters(PAGE_NUMBER_PARAM, PAGE_SIZE_PARAM, STATUS_FILTER_PARAM)
        val pagination = Pagination.from(
            pageParam = call.request.queryParameters[PAGE_NUMBER_PARAM],
            sizeParam = call.request.queryParameters[PAGE_SIZE_PARAM],
        )
        val statuses = validateEnumListParam<AuthorizationRequestStatus>(
            call.request.queryParameters[STATUS_FILTER_PARAM],
            STATUS_FILTER_PARAM,
        )

        val page = handler(call.authorizedAgent, pagination, statuses)
        call.respond(HttpStatusCode.OK, page.toGetCollectionResponse(statuses))
    }
}
