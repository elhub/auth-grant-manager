package no.elhub.auth.features.documents.create

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.elhub.auth.features.documents.common.toResponse

fun Route.createDocumentRoute(handler: Handler) {
    post {
        val requestBody = call.receive<HttpRequestBody>()

        val command = requestBody.toCommand()
            .getOrElse { errors ->
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

        val document = handler(command)
            .getOrElse { error ->
                when (error) {
                    is
                    Error.DocumentGenerationError,
                    Error.MappingError,
                    Error.SignatureFetchingError,
                    Error.SigningDataGenerationError,
                    Error.SigningError,
                    Error.PersistenceError
                        -> call.respond(HttpStatusCode.InternalServerError)
                }
                return@post
            }

        call.respond(status = HttpStatusCode.Created, message = document.toResponse())
    }
}
