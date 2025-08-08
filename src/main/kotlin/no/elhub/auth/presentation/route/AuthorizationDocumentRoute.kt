package no.elhub.auth.presentation.route

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.elhub.auth.domain.document.AuthorizationDocument
import no.elhub.auth.domain.document.AuthorizationDocumentHandler
import no.elhub.auth.domain.document.CreateAuthorizationDocumentCommand
import no.elhub.auth.presentation.jsonapi.PostAuthorizationDocumentRequest
import no.elhub.auth.presentation.jsonapi.ResponseMeta
import no.elhub.auth.presentation.jsonapi.toPostAuthorizationDocumentResponse
import java.util.*

fun Route.documentRoutes(documentService: AuthorizationDocumentHandler) {
    post {
        val requestBody = call.receive<PostAuthorizationDocumentRequest>()

        val createDocumentCommand = CreateAuthorizationDocumentCommand(
            type = AuthorizationDocument.DocumentType.ChangeOfSupplierConfirmation,
            requestedBy = requestBody.data.relationships.requestedBy.data.id,
            requestedTo = requestBody.data.relationships.requestedTo.data.id,
            meteringPoint = requestBody.data.attributes.meteringPoint,
        )
        val authorizationDocument = documentService.createDocument(createDocumentCommand)

        val responseBody = authorizationDocument.toPostAuthorizationDocumentResponse()

        call.respond(status = HttpStatusCode.Created, message = responseBody)
    }

    route("/{$ID}") {
        get {
            call.respond(status = HttpStatusCode.OK, message = ResponseMeta())
        }
        patch {
            call.respond(status = HttpStatusCode.OK, message = ResponseMeta())
        }
    }
    get {
        call.respond(status = HttpStatusCode.OK, message = ResponseMeta())
    }
    get("/{$ID}.pdf") {
        val documentId = call.parameters[ID]
            ?.toValidUuidOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid or missing document ID format")

        val fileBytes = documentService.getDocumentFile(documentId)
        if (fileBytes == null) {
            call.respond(HttpStatusCode.NotFound, "Document not found")
            return@get
        }
        call.respondBytes(fileBytes, ContentType.Application.Pdf)
    }
}

fun String.toValidUuidOrNull(): UUID? =
    runCatching { UUID.fromString(this) }.getOrNull()
