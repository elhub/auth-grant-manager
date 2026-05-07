package no.elhub.auth.features.documents.create

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.toApiErrorResponse
import no.elhub.auth.features.common.receiveEither
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.toTypeMismatchApiErrorResponse
import no.elhub.auth.features.documents.create.dto.JsonApiCreateDocumentRequest
import no.elhub.auth.features.documents.create.dto.toCreateDocumentResponse
import no.elhub.auth.features.documents.create.dto.toModel
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(Route::class.java)

fun Route.route(
    handler: Handler,
    authProvider: AuthorizationProvider,
) {
    post {
        val resolvedActor = authProvider.authorize(call)
            .getOrElse {
                val error = it.toApiErrorResponse()
                call.respond(error.first, error.second)
                return@post
            }

        val requestBody = call.receiveEither<JsonApiCreateDocumentRequest>()
            .getOrElse { error ->
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@post
            }

        if (requestBody.data.type != "AuthorizationDocument") {
            val (status, message) = toTypeMismatchApiErrorResponse(
                expectedType = "AuthorizationDocument",
                actualType = requestBody.data.type
            )
            call.respond(status, message)
            return@post
        }

        val model = requestBody.toModel(resolvedActor)

        val document = handler(model)
            .getOrElse { error ->
                logger.error("Failed to create authorization document: {}", error)
                val (status, validationError) = error.toApiErrorResponse()
                call.respond(status, validationError)
                return@post
            }

        call.respond(
            status = HttpStatusCode.Created,
            message = document.toCreateDocumentResponse()
        )
    }
}
