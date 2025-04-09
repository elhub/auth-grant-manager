package no.elhub.auth.features.documents

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.elhub.auth.config.ID

fun Route.documents(routePath: String, documentService: AuthorizationDocumentService) {
    route(routePath) {
        get {
            documentService.getDocuments(call)
        }
        post {
            documentService.postDocument(call)
        }
    }
    route("$routePath/$ID") {
        get {
            documentService.getDocumentById(call)
        }
        patch {
            documentService.patchDocumentById(call)
        }
    }
}
