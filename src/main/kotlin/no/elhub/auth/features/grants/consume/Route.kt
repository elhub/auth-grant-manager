package no.elhub.auth.features.grants.consume

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import no.elhub.auth.features.common.auth.authorizedParty
import no.elhub.auth.features.common.receiveEither
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.toTypeMismatchApiErrorResponse
import no.elhub.auth.features.common.validateDataId
import no.elhub.auth.features.common.validatePathId
import no.elhub.auth.features.grants.common.dto.toSingleGrantResponse
import no.elhub.auth.features.grants.consume.dto.JsonApiConsumeRequest
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(Route::class.java)

const val GRANT_ID_PARAM = "id"

fun Route.route(handler: Handler) {
    patch("/{$GRANT_ID_PARAM}") {
        val grantId = validatePathId(call.parameters[GRANT_ID_PARAM])
            .getOrElse { error ->
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@patch
            }

        val requestBody = call.receiveEither<JsonApiConsumeRequest>()
            .getOrElse { error ->
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@patch
            }

        validateDataId(requestBody.data.id, grantId)
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, body)
                return@patch
            }

        if (requestBody.data.type != "AuthorizationGrant") {
            val (status, message) = toTypeMismatchApiErrorResponse(
                expectedType = "AuthorizationGrant",
                actualType = requestBody.data.type
            )
            call.respond(status, message)
            return@patch
        }

        val command = ConsumeCommand(
            grantId = grantId,
            newStatus = requestBody.data.attributes.status,
            authorizedParty = call.authorizedParty
        )

        val updated = handler(command).getOrElse { error ->
            logger.error("Failed to update authorization grant: {}", error)
            val (status, error) = error.toApiErrorResponse()
            call.respond(status, error)
            return@patch
        }

        call.respond(HttpStatusCode.OK, updated.toSingleGrantResponse())
    }
}
