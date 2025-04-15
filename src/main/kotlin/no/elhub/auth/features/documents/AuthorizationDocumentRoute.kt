package no.elhub.auth.features.documents

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
import no.elhub.auth.config.ID
import no.elhub.auth.features.documents.jsonApiSpec.PostAuthorizationDocument
import java.util.*
fun Route.documentRoutes(documentService: AuthorizationDocumentService) {
    post {
        val requestBody = call.receive<PostAuthorizationDocument.Request>()
        val authorizationDocument = documentService.postDocument(requestBody)

        val response = PostAuthorizationDocument.Response(
            data = PostAuthorizationDocument.Response.Data(
                id = authorizationDocument.id.toString(),
                type = "AuthorizationDocument",
                attributes = PostAuthorizationDocument.Response.Data.Attributes(
                    requestedBy = authorizationDocument.requestedBy,
                    requestedTo = authorizationDocument.requestedTo,
                    createdAt = authorizationDocument.createdAt.toString(),
                    updatedAt = authorizationDocument.updatedAt.toString(),
                    status = authorizationDocument.status.toString()
                )
            ),
        )
        call.respond(status = HttpStatusCode.OK, message = response)
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
