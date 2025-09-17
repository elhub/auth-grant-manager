package no.elhub.auth.features.documents.create

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.documents.common.toResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

fun Route.createDocumentRoute(createHandler: CreateDocumentHandler) {
    post {
        val requestBody = call.receive<CreateDocumentRequest>()

        val document = createHandler(requestBody.toCreateDocumentCommand())
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
