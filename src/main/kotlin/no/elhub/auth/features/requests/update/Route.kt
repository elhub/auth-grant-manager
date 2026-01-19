package no.elhub.auth.features.requests.update

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.elhub.auth.features.common.auth.AuthError
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.toApiErrorResponse
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validateId
import no.elhub.auth.features.requests.update.dto.JsonApiUpdateRequest
import no.elhub.auth.features.requests.update.dto.toUpdateResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject

const val REQUEST_ID_PARAM = "id"

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
        val command = UpdateCommand(
            requestId = requestId,
            newStatus = requestBody.data.attributes.status,
            authorizedParty = AuthorizationParty(
                resourceId = resolvedActor.id.toString(),
                type = PartyType.Person
            )
        )

        val updated = handler(command).getOrElse { error ->
            when (error) {
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

                UpdateError.IllegalStateError ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonApiErrorObject(
                            status = "400",
                            title = "Invalid Status State",
                            detail = "Request must be in 'Pending' status to update."
                        )
                    )

                UpdateError.ExpiredError ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonApiErrorObject(
                            status = "400",
                            title = "Request Has Expired",
                            detail = "Request validity period has passed"
                        )
                    )

                UpdateError.NotAuthorizedError -> {
                    val (status, body) = AuthError.NotAuthorized.toApiErrorResponse()
                    call.respond(status, body)
                }
            }
            return@patch
        }

        call.respond(HttpStatusCode.OK, updated.toUpdateResponse())
    }
}
