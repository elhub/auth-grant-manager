package no.elhub.auth.features.grants

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.util.url
import no.elhub.auth.config.ID

fun Route.grants(grantHandler: AuthorizationGrantHandler) {
    get {
        val result = grantHandler.getGrants()
        call.respond(status = HttpStatusCode.OK, message = AuthorizationGrantResponseCollection.from(result, call.url()))
    }
    route("/{$ID}") {
        get {
            grantHandler.getGrantById(call)
        }
    }
}
