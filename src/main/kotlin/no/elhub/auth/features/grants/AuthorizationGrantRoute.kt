package no.elhub.auth.features.grants

import arrow.core.Either.Left
import arrow.core.Either.Right
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.util.url
import no.elhub.auth.config.ID
import no.elhub.auth.features.errors.ApiErrorJson
import no.elhub.auth.features.utils.validateId

fun Route.grants(grantHandler: AuthorizationGrantHandler) {
    get {
        val result = grantHandler.getGrants()
        call.respond(status = HttpStatusCode.OK, message = AuthorizationGrantResponseCollection.from(result, call.url()))
    }

    route("/{$ID}") {
        get {
            val idResult = validateId(call.parameters[ID])
            if (idResult is Left) {
                call.respond(
                    HttpStatusCode.fromValue(idResult.value.status),
                    ApiErrorJson.from(idResult.value, call.url()),
                )
                return@get
            }

            val id = (idResult as Right).value
            when (val result = grantHandler.getGrant(id)) {
                is Left ->
                    call.respond(
                        HttpStatusCode.fromValue(result.value.status),
                        ApiErrorJson.from(result.value, call.url()),
                    )
                is Right ->
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = AuthorizationGrantResponse.from(result.value, selfLink = call.url()),
                    )
            }
        }
    }
}
