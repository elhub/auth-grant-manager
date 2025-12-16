package no.elhub.auth.features.requests.update

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.elhub.auth.features.common.InputError
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validateId
import no.elhub.auth.features.requests.update.dto.JsonApiUpdateRequest
import no.elhub.auth.features.requests.update.dto.toUpdateResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject

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
            call.receive<JsonApiUpdateRequest>()
        }.getOrElse {
            val (status, body) = InputError.MalformedInputError.toApiErrorResponse()
            call.respond(status, JsonApiErrorCollection(listOf(body)))
            return@patch
        }

        val command = UpdateCommand(
            requestId = requestId,
            newStatus = requestBody.data.attributes.status
        )

        val updated = handler(command).getOrElse { error ->
            when (error) {
                is
                UpdateError.PersistenceError,
                UpdateError.RequestNotFound,
                UpdateError.GrantCreationError,
                UpdateError.ScopeReadError,
                -> call.respond(HttpStatusCode.InternalServerError)

                UpdateError.IllegalTransitionError ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonApiErrorObject(
                            status = "400",
                            title = "Invalid Status Transition",
                            detail = "Only 'Accepted' and 'Rejected' statuses are allowed."
                        )
                    )
            }
            return@patch
        }

        call.respond(HttpStatusCode.OK, updated.toUpdateResponse())
    }
}
