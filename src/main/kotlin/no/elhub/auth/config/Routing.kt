package no.elhub.auth.config

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.features.documents.AuthorizationDocumentHandler
import no.elhub.auth.features.documents.documentRoutes
import no.elhub.auth.features.grants.AuthorizationGrantHandler
import no.elhub.auth.features.grants.grants
import no.elhub.auth.features.requests.AuthorizationRequestHandler
import no.elhub.auth.features.requests.requestRoutes
import org.koin.ktor.ext.inject

const val AUTHORIZATION_API = ""
const val HEALTH = "$AUTHORIZATION_API/health"
const val AUTHORIZATION_DOCUMENT = "$AUTHORIZATION_API/authorization-documents"
const val AUTHORIZATION_GRANT = "$AUTHORIZATION_API/authorization-grants"
const val AUTHORIZATION_REQUEST = "$AUTHORIZATION_API/authorization-requests"
const val ID = "id"

fun Application.configureRouting() {
    val documentHandler by inject<AuthorizationDocumentHandler>()
    val grantHandler by inject<AuthorizationGrantHandler>()
    val requestHandler by inject<AuthorizationRequestHandler>()

    routing {
        route(AUTHORIZATION_DOCUMENT) {
            documentRoutes(documentHandler)
        }
        route(AUTHORIZATION_GRANT) {
            grants(grantHandler)
        }
        route(AUTHORIZATION_REQUEST) {
            requestRoutes(requestHandler)
        }
        get(HEALTH) {
            call.respondText("OK", status = HttpStatusCode.OK)
        }
        swaggerUI(path = "openapi", swaggerFile = "openapi.yaml")
        staticResources("schemas/", "schemas")
    }
}
