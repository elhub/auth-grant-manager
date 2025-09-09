package no.elhub.auth.features.documents.create

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.toResponse
import no.elhub.auth.features.documents.get.GetDocumentHandler
import no.elhub.auth.features.documents.get.GetDocumentQuery
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

fun Route.createDocumentRoute(createHandler: CreateDocumentHandler, getHandler: GetDocumentHandler) {
    post {
        val createDocumentRequest = call.receive<CreateDocumentRequest>()

        val command = when (createDocumentRequest.data.attributes.documentType) {
            AuthorizationDocument.Type.ChangeOfSupplierConfirmation -> createDocumentRequest.toCreateDocumentChangeOfSupplierCommand()
        }

        val documentId = createHandler(command)
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
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@post
            }

        call.respond(status = HttpStatusCode.Created, message = authorizationDocument.toResponse())
    }
}
