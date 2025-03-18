package no.elhub.auth.services.documents

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.documents(routePath: String, documentService: AuthorizationDocumentService) {
    route(routePath) {
        get {
            documentService.getDocuments(call)
        }
        post {
            documentService.postDocument(call)
        }
    }
}

fun Route.documentById(routePath: String, documentService: AuthorizationDocumentService) {
    route(routePath) {
        get {
            documentService.getDocumentById(call)
        }
        patch {
            documentService.patchDocumentById(call)
        }
    }
}
