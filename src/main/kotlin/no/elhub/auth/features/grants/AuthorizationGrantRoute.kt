package no.elhub.auth.features.grants

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.elhub.auth.config.ID

fun Route.grants(grantService: AuthorizationGrantService) {
    get {
        grantService.getGrants(call)
    }
    route("/$ID") {
        get {
            grantService.getGrantById(call)
        }
    }
}
