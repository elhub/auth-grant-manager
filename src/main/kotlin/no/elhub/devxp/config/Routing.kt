package no.elhub.devxp.config

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.elhub.devxp.ping.PingService
import org.koin.ktor.ext.inject

const val TEMPLATE_API = ""
const val PING = "$TEMPLATE_API/ping"

fun Application.configureRouting() {
    val pingService by inject<PingService>()

    routing {
        get(PING) {
            call.respondText(pingService.ping())
        }
    }
}
