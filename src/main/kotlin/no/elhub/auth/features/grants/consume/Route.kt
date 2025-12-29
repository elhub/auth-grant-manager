package no.elhub.auth.features.grants.consume

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.elhub.auth.features.common.InputError
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validateId
import no.elhub.auth.features.grants.common.dto.toSingleGrantResponse
import no.elhub.auth.features.grants.consume.dto.JsonApiConsumeRequest
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

const val GRANT_ID_PARAM = "id"

fun Route.route(handler: Handler) {
    patch("/{$GRANT_ID_PARAM}") {
        val grantId = validateId(call.parameters[GRANT_ID_PARAM])
            .getOrElse { error ->
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@patch
            }

        val body = runCatching {
            call.receive<JsonApiConsumeRequest>()
        }.getOrElse {
            val (status, body) = InputError.MalformedInputError.toApiErrorResponse()
            call.respond(status, JsonApiErrorCollection(listOf(body)))
            return@patch
        }

        val command = ConsumeCommand(
            grantId = grantId,
            newStatus = body.data.attributes.status,
        )

        val updated = handler(command).getOrElse { error ->
            when (error) {
                is
                ConsumeError.PersistenceError,
                ConsumeError.GrantNotFound
                -> call.respond(HttpStatusCode.InternalServerError)
            }
            return@patch
        }

        call.respond(HttpStatusCode.OK, updated.toSingleGrantResponse())
    }
}
