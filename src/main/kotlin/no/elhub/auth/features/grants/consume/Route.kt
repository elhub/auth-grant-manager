package no.elhub.auth.features.grants.consume

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.elhub.auth.features.common.InputError
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.toApiErrorResponse
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.validateId
import no.elhub.auth.features.grants.common.dto.toSingleGrantResponse
import no.elhub.auth.features.grants.consume.dto.JsonApiConsumeRequest
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject

const val GRANT_ID_PARAM = "id"

fun Route.route(handler: Handler, authProvider: AuthorizationProvider) {
    patch("/{$GRANT_ID_PARAM}") {
        val authorizedSystem = authProvider.authorizeElhubService(call)
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, body)
                return@patch
            }

        val grantId = validateId(call.parameters[GRANT_ID_PARAM])
            .getOrElse { error ->
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@patch
            }

        val body = runCatching {
            call.receive<JsonApiConsumeRequest>()
        }.getOrElse {
            val (status, body) = InputError.MalformedInputError.toApiErrorResponse()
            call.respond(status, body)
            return@patch
        }

        val command = ConsumeCommand(
            grantId = grantId,
            newStatus = body.data.attributes.status,
            authorizedParty = AuthorizationParty(
                resourceId = authorizedSystem.id,
                type = PartyType.System
            )
        )

        val updated = handler(command).getOrElse { error ->
            when (error) {
                is ConsumeError.PersistenceError ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        message = listOf(
                            JsonApiErrorObject(
                                status = HttpStatusCode.InternalServerError.toString(),
                                code = "internal_error",
                            )
                        )
                    )

                is ConsumeError.GrantNotFound ->
                    call.respond(
                        HttpStatusCode.NotFound,
                        message = listOf(
                            JsonApiErrorObject(
                                status = HttpStatusCode.NotFound.toString(),
                                code = "not_found"
                            )
                        )
                    )

                is ConsumeError.NotAuthorized ->
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        message = listOf(
                            JsonApiErrorObject(
                                status = HttpStatusCode.Unauthorized.toString(),
                                code = "not_authorized"
                            )
                        )
                    )
            }
            return@patch
        }

        call.respond(HttpStatusCode.OK, updated.toSingleGrantResponse())
    }
}
