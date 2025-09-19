package no.elhub.auth.features.health

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

const val HEALTH_PATH = "/health"

fun Application.module() {
    routing {
        route(HEALTH_PATH) {
            get {
                call.respondText("OK", status = HttpStatusCode.OK)
            }
        }
    }
}
