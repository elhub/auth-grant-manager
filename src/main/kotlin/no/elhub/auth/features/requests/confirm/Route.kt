package no.elhub.auth.features.requests.confirm

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.elhub.auth.features.common.InputError
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validateId
import no.elhub.auth.features.requests.confirm.dto.JsonApiConfirmRequest
import no.elhub.auth.features.requests.create.dto.toCreateResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

const val REQUEST_ID_PARAM = "id"

fun Route.route(handler: Handler) {
    patch("/{$REQUEST_ID_PARAM}") {
        val requestId = validateId(call.parameters[REQUEST_ID_PARAM])
            .getOrElse { error ->
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@patch
            }

        val requestBody = runCatching {
            call.receive<JsonApiConfirmRequest>()
        }.getOrElse {
            val (status, body) = InputError.MalformedInputError.toApiErrorResponse()
            call.respond(status, JsonApiErrorCollection(listOf(body)))
            return@patch
        }

        val command = ConfirmCommand(
            requestId = requestId,
            newStatus = requestBody.data.attributes.status
        )

        val updated = handler(command).getOrElse { error ->
            when (error) {
                is
                ConfirmError.PersistenceError,
                ConfirmError.RequestNotFound
                -> call.respond(HttpStatusCode.InternalServerError)
            }
            return@patch
        }

        // TODO need reference to auth-grant in the response when accepted
        call.respond(HttpStatusCode.OK, updated.toCreateResponse())
    }
}
