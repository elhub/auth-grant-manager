package no.elhub.auth.features.grants

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.util.url
import no.elhub.auth.features.errors.httpStatus
import org.koin.core.annotation.Single
import java.util.UUID

@Single
class AuthorizationGrantHandler {
    suspend fun getAllGrants(call: ApplicationCall) {
        AuthorizationGrantRepository.findAll().fold(
            ifLeft = { err ->
                call.respond(
                    status = err.httpStatus(),
                    message = err,
                )
            },
            ifRight = { grants ->
                call.respond(
                    status = HttpStatusCode.OK,
                    message = AuthorizationGrantResponseCollection.from(grants, call.url()),
                )
            },
        )
    }

    suspend fun getGrantById(call: ApplicationCall) {
        val id = call.extractUuidParameter("ID") ?: return

        AuthorizationGrantRepository.findById(id).fold(
            ifLeft = { err ->
                call.respond(
                    status = err.httpStatus(),
                    message = err,
                )
            },
            ifRight = { result ->
                call.respond(
                    status = HttpStatusCode.OK,
                    message = AuthorizationGrantResponse.from(result, selfLink = call.url()),
                )
            },
        )
    }

    private suspend fun ApplicationCall.extractUuidParameter(paramName: String = "ID"): UUID? {
        val rawId =
            parameters[paramName] ?: run {
                respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing or malformed id"))
                return null
            }
        return try {
            UUID.fromString(rawId)
        } catch (e: IllegalArgumentException) {
            respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid UUID"))
            null
        }
    }
}
