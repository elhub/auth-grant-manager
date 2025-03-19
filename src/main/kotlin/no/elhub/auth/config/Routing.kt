package no.elhub.auth.config

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import no.elhub.auth.services.documents.AuthorizationDocumentService
import no.elhub.auth.services.documents.documents
import no.elhub.auth.services.grants.AuthorizationGrantService
import no.elhub.auth.services.grants.grants
import no.elhub.auth.services.requests.AuthorizationRequestService
import no.elhub.auth.services.requests.requests
import org.koin.ktor.ext.inject

const val AUTHORIZATION_API = ""
const val HEALTH = "$AUTHORIZATION_API/health"
const val AUTHORIZATION_DOCUMENT = "$AUTHORIZATION_API/authorization-documents"
const val AUTHORIZATION_GRANT = "$AUTHORIZATION_API/authorization-grants"
const val AUTHORIZATION_REQUEST = "$AUTHORIZATION_API/authorization-requests"
const val ID = "{id}"

fun Application.configureRouting() {
    val documentService by inject<AuthorizationDocumentService>()
    val grantService by inject<AuthorizationGrantService>()
    val requestService by inject<AuthorizationRequestService>()

    routing {
        grants(AUTHORIZATION_GRANT, grantService)
        documents(AUTHORIZATION_DOCUMENT, documentService)
        requests(AUTHORIZATION_REQUEST, requestService)
        get(HEALTH) {
            call.respondText("OK", status = HttpStatusCode.OK)
        }
    }
}
