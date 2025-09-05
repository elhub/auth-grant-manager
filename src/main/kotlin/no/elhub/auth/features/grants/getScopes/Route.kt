package no.elhub.auth.features.grants.getScopes

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.util.url
import no.elhub.auth.features.common.validateId
import no.elhub.auth.config.ID
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.grants.common.toResponse
import java.util.UUID
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject

fun Route.getGrantScopesRoute(handler: GetGrantScopesHandler) {
    route("/{$ID}/scopes") {
        get {
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
}
