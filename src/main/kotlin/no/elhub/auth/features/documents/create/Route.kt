package no.elhub.auth.features.documents.create

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.documents.common.toResponse
import no.elhub.auth.features.documents.get.GetDocumentHandler
import no.elhub.auth.features.documents.get.GetDocumentQuery

fun Route.createDocumentRoute(createHandler: CreateDocumentHandler, getHandler: GetDocumentHandler) {
    post {
        val requestBody = call.receive<HttpRequestBody>()

        val documentId = createHandler(requestBody.toCreateDocumentCommand())
            .getOrElse { error ->
                when (error) {
                    is
                    CreateDocumentError.DocumentGenerationError,
                    CreateDocumentError.MappingError,
                    CreateDocumentError.SignatureFetchingError,
                    CreateDocumentError.SigningDataGenerationError,
                    CreateDocumentError.SigningError,
                    CreateDocumentError.PersistenceError
                        -> call.respond(HttpStatusCode.InternalServerError)
                }
                return@post
            }

        val authorizationDocument = getHandler(GetDocumentQuery(documentId))
            .getOrElse { error ->
                when (error) {
                    is QueryError.ResourceNotFoundError -> call.respond(HttpStatusCode.NotFound)
                    is QueryError.IOError -> call.respond(HttpStatusCode.InternalServerError)
                }
                return@post
            }

        call.respond(status = HttpStatusCode.Created, message = authorizationDocument.toResponse())
    }

}
