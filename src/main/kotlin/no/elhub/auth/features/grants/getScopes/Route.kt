package no.elhub.auth.features.grants.getScopes

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.features.common.validateId
import no.elhub.auth.config.ID
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.grants.common.toResponse
import java.util.UUID
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

fun Route.getGrantScopesRoute(handler: GetGrantScopesHandler) {
    get("/{$ID}/scopes") {
        val id: UUID = validateId(call.parameters[ID])
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }

        val scopes = handler(GetGrantScopesQuery(id))
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@get
            }

        call.respond(HttpStatusCode.OK, scopes.toResponse())
    }
}
