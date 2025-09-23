package no.elhub.auth.features.documents.create

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.elhub.auth.features.documents.common.toResponse

fun Route.createDocumentRoute(handler: CreateDocumentHandler) {
    post {
        val requestBody = call.receive<CreateDocumentRequest>()

        val command = requestBody.toCreateDocumentCommand()
            .getOrElse { errors ->
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

        val document = handler(command)
            .getOrElse { error ->
                when (error) {
                    is
                    CreateDocumentError.FileGenerationError,
                    CreateDocumentError.CertificateRetrievalError,
                    CreateDocumentError.MappingError,
                    CreateDocumentError.SignatureFetchingError,
                    CreateDocumentError.SigningDataGenerationError,
                    CreateDocumentError.SigningError,
                    CreateDocumentError.PersistenceError
                        -> call.respond(HttpStatusCode.InternalServerError)
                }
                return@post
            }

        call.respond(status = HttpStatusCode.Created, message = document.toResponse())
    }
}
