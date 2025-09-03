package no.elhub.auth.features.requests.get

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.util.url
import no.elhub.auth.config.ID
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validateId
import no.elhub.auth.features.requests.common.toResponse
import java.util.UUID

fun Route.getRequestRoute(handler: GetRequestHandler) {
    get("/$ID") {
        val id: UUID = validateId(call.parameters[ID])
            .getOrElse { err ->
                call.respond(HttpStatusCode.BadRequest, err.toApiErrorResponse())
                return@get
            }

        val authorizationRequest = handler(GetRequestQuery(id)).getOrElse { error ->
            when (error) {
                is QueryError.ResourceNotFoundError -> call.respond(
                    HttpStatusCode.NotFound,
                    "Authorization request not found"
                )

                is QueryError.IOError -> call.respond(
                    HttpStatusCode.InternalServerError,
                    "An error occurred when attempting to retrieve the authorization request from the database"
                )
            }
            return@get
        }

        call.respond(HttpStatusCode.OK, authorizationRequest.toResponse())
    }
}
