package no.elhub.auth.features.grants.get

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.util.url
import no.elhub.auth.config.ID
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validateId
import no.elhub.auth.features.grants.common.toResponse
import java.util.UUID

fun Route.getGrantRoute(handler: GetGrantHandler) {
    route("/{$ID}") {
        get {
            val id: UUID = validateId(call.parameters[ID])
                .getOrElse { err ->
                    call.respond(HttpStatusCode.BadRequest, err.toApiErrorResponse())
                    return@get
                }

            val grant = handler(GetGrantQuery(id)).getOrElse { error ->
                when (error) {
                    is QueryError.ResourceNotFoundError ->
                        call.respond(
                            HttpStatusCode.NotFound,
                            "Authorization grant with id=$id not found",
                        )

                    is QueryError.IOError ->
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            "Unexpected error occurred during fetch authorization grant with id=$id",
                        )
                }

                return@get
            }

            call.respond(HttpStatusCode.OK, grant.toResponse())
        }
    }
}
