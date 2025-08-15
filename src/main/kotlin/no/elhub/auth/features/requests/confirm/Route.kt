package no.elhub.auth.features.requests.confirm

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import io.ktor.server.util.url
import no.elhub.auth.config.ID
import java.util.UUID

fun Route.confirmRequestRoute(handler: ConfirmRequestHandler) {
    patch {
        call.respond(HttpStatusCode.NotImplemented)
    }
}
