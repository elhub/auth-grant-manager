package no.elhub.auth.v1.api.grants.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.patch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import no.elhub.auth.common.auth.authorizedParty
import no.elhub.auth.v1.api.common.errors.InputError
import no.elhub.auth.v1.api.grants.request.JsonApiUpdateAuthorizationGrantRequest
import no.elhub.auth.v1.api.grants.response.toGetResponse
import no.elhub.auth.v1.features.grants.UpdateGrantHandler
import java.util.UUID

@OptIn(ExperimentalSerializationApi::class)
fun Route.route(handler: UpdateGrantHandler) {
    patch("/{id}") {
        val pathId = call.parameters["id"]?.takeIf { it.isNotBlank() }
            ?: throw InputError.MissingInputError("Missing authorization grant ID")
        val id = try {
            UUID.fromString(pathId)
        } catch (_: IllegalArgumentException) {
            throw InputError.MalformedInputError("Authorization grant ID must be a UUID")
        }

        val request = try {
            call.receive<JsonApiUpdateAuthorizationGrantRequest>()
        } catch (error: Exception) {
            when (val root = generateSequence(error as Throwable) { it.cause }.last()) {
                is MissingFieldException -> throw InputError.MissingFieldError(root.missingFields)
                is SerializationException -> throw InputError.InvalidFieldValueError("Invalid patch body")
                else -> throw error
            }
        }

        if (request.data.id.isBlank()) {
            throw InputError.MissingInputError("Missing data.id")
        }
        val dataId = try {
            UUID.fromString(request.data.id)
        } catch (_: IllegalArgumentException) {
            throw InputError.IdMismatchError("data.id must be a UUID matching the path ID")
        }
        if (dataId != id) {
            throw InputError.IdMismatchError("data.id must match the path ID")
        }

        val updated = handler(
            id = id,
            authorizedParty = call.authorizedParty,
            status = request.data.attributes.status,
        )
        call.respond(HttpStatusCode.OK, updated.toGetResponse())
    }
}
