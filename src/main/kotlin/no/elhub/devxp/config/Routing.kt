package no.elhub.devxp.config

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.elhub.devxp.services.grants.AuthorizationGrantService
import no.elhub.devxp.services.ping.PingService
import org.koin.ktor.ext.inject

const val TEMPLATE_API = ""
const val PING = "$TEMPLATE_API/ping"
const val HEALTH = "$TEMPLATE_API/health"
const val CONSENT_REQUEST = "$TEMPLATE_API/authorization-grant"

fun Application.configureRouting() {
    val pingService by inject<PingService>()
    val grantService by inject<AuthorizationGrantService>()

    routing {
        get(PING) {
            call.respondText(pingService.ping())
        }
        get(HEALTH) {
            call.respondText("OK", status = HttpStatusCode.OK)
        }
        // This is a dummy endpoint. It should be replaced with the actual service.
        get(CONSENT_REQUEST) {
            call.respondText(grantService.createGrant())
        }
    }
}
