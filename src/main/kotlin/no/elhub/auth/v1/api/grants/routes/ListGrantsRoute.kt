package no.elhub.auth.v1.api.grants.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.common.auth.authorizedParty
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.api.grants.response.toGetCollectionResponse
import no.elhub.auth.v1.features.grants.ListGrantsHandler

fun Route.route(handler: ListGrantsHandler) {
    get {
        val pagination = Pagination.from(
            pageParam = call.request.queryParameters["page[number]"],
            sizeParam = call.request.queryParameters["page[size]"],
        )

        val page = handler(call.authorizedParty, pagination)
        call.respond(HttpStatusCode.OK, page.toGetCollectionResponse())
    }
}
