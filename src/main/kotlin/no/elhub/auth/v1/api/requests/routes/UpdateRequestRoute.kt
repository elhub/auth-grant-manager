package no.elhub.auth.v1.api.requests.routes

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.elhub.auth.v0.features.common.receiveEither
import no.elhub.auth.v0.features.common.toApiErrorResponse
import no.elhub.auth.v0.features.common.toTypeMismatchApiErrorResponse
import no.elhub.auth.v0.features.common.validateDataId
import no.elhub.auth.v0.features.common.validatePathId
import no.elhub.auth.v1.api.common.validateQueryParameters
import no.elhub.auth.v1.api.requests.request.JsonApiUpdateAuthorizationRequestRequest
import no.elhub.auth.v1.api.requests.response.toGetResponse
import no.elhub.auth.v1.common.auth.authorizedAgent
import no.elhub.auth.v1.features.requests.UpdateRequestHandler

fun Route.route(handler: UpdateRequestHandler) {
    patch("/{id}") {
        call.validateQueryParameters()

        val id = validatePathId(call.parameters["id"])
            .getOrElse { error ->
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@patch
            }

        val request = call.receiveEither<JsonApiUpdateAuthorizationRequestRequest>()
            .getOrElse { error ->
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@patch
            }

        validateDataId(request.data.id, id)
            .getOrElse { error ->
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@patch
            }

        if (request.data.type != "AuthorizationRequest") {
            val (status, body) = toTypeMismatchApiErrorResponse(
                expectedType = "AuthorizationRequest",
                actualType = request.data.type,
            )
            call.respond(status, body)
            return@patch
        }

        val updated = handler(
            id = id,
            authorizedAgent = call.authorizedAgent,
            status = request.data.attributes.status,
            textLanguage = request.data.attributes.textLanguage,
        )
        call.respond(HttpStatusCode.OK, updated.toGetResponse())
    }
}
