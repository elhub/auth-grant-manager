package no.elhub.auth.features.grants

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.elhub.auth.config.ID

fun Route.grants(grantHandler: AuthorizationGrantHandler) {
    route("") {
        get {
            grantHandler.getAllGrants(call)
        }

        get("/{$ID}") {
            grantHandler.getGrantById(call)
        }
    }
}
