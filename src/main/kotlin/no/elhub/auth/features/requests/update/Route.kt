package no.elhub.auth.features.requests.update

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.toApiErrorResponse
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.toTypeMismatchApiErrorResponse
import no.elhub.auth.features.common.validateId
import no.elhub.auth.features.requests.update.dto.JsonApiUpdateRequest
import no.elhub.auth.features.requests.update.dto.toUpdateResponse
import org.slf4j.LoggerFactory

const val REQUEST_ID_PARAM = "id"
private val logger = LoggerFactory.getLogger(Route::class.java)

fun Route.route(
    handler: Handler,
    authProvider: AuthorizationProvider
) {
    patch("/{$REQUEST_ID_PARAM}") {
        val resolvedActor = authProvider.authorizeEndUser(call)
            .getOrElse {
                val error = it.toApiErrorResponse()
                call.respond(error.first, error.second)
                return@patch
            }

        val requestId = validateId(call.parameters[REQUEST_ID_PARAM])
            .getOrElse { error ->
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@patch
            }

        val requestBody = call.receive<JsonApiUpdateRequest>()

        if (requestBody.data.type != "AuthorizationRequest") {
            val (status, message) = toTypeMismatchApiErrorResponse(
                expectedType = "AuthorizationRequest",
                actualType = requestBody.data.type
            )
            call.respond(status, message)
            return@patch
        }

        val command = UpdateCommand(
            requestId = requestId,
            newStatus = requestBody.data.attributes.status,
            authorizedParty = AuthorizationParty(
                id = resolvedActor.id.toString(),
                type = PartyType.Person
            )
        )

        val updated = handler(command).getOrElse { error ->
            logger.error("Failed to update authorization request: {}", error)
            val (status, error) = error.toApiErrorResponse()
            call.respond(status, error)
            return@patch
        }

        call.respond(HttpStatusCode.OK, updated.toUpdateResponse())
    }
}
