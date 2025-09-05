package no.elhub.auth.features.grants.query

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.util.url
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.grants.common.toResponse

fun Route.queryGrantsRoute(handler: QueryGrantsHandler) {
    get {
        // TODO("Build query from payload")
        val grants = handler(QueryGrantsQuery()).getOrElse { error ->
            when (error) {
                is QueryError.ResourceNotFoundError ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Unexpected error occurred during fetch authorization grants",
                    )

                is QueryError.IOError ->
                    call.respond(
                        HttpStatusCode.NotFound,
                        "Authorization grants not found",
                    )
            }
            return@get
        }

        call.respond(HttpStatusCode.OK, grants.toResponse())
    }
}
