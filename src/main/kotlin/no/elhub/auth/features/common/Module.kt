package no.elhub.auth.features.common

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

fun Application.module() {
    routing {
        route("{...}") {
            handle {
                val (status, body) = buildApiErrorResponse(
                    HttpStatusCode.NotFound,
                    "Endpoint not found",
                    "The requested endpoint does not exist. Please check the URL."
                )
                call.respond<JsonApiErrorCollection>(status, body)
            }
        }
    }
}
