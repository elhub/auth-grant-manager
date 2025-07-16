package no.elhub.auth.grantmanager.presentation.features.documents

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
import no.elhub.auth.grantmanager.presentation.config.ID
import java.util.*

fun Route.documentRoutes(documentService: AuthorizationDocumentHandler) {
    post {
        val requestBody = call.receive<PostAuthorizationDocumentRequest>()
        val authorizationDocument = documentService.postDocument(requestBody)

        val responseBody = authorizationDocument.toPostAuthorizationDocumentResponse()

        call.respond(status = HttpStatusCode.Created, message = responseBody)
    }

    route("/{$ID}") {
        get {
            documentService.getDocumentById(call)
        }
        patch {
            documentService.patchDocumentById(call)
        }
    }
    get {
        documentService.getDocuments(call)
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
