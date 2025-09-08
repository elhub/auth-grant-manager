package no.elhub.auth.config

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.features.documents.confirm.confirmDocumentRoute
import no.elhub.auth.features.documents.create.createDocumentRoute
import no.elhub.auth.features.documents.get.getDocumentRoute
import no.elhub.auth.features.grants.get.getGrantRoute
import no.elhub.auth.features.grants.getScopes.getGrantScopesRoute
import no.elhub.auth.features.grants.query.queryGrantsRoute
import no.elhub.auth.features.requests.create.createRequestRoute
import no.elhub.auth.features.requests.confirm.confirmRequestRoute
import no.elhub.auth.features.requests.get.getRequestRoute
import no.elhub.auth.features.requests.query.queryRequestRoute
import org.koin.ktor.ext.get

const val AUTHORIZATION_API = ""
const val HEALTH = "$AUTHORIZATION_API/health"
const val AUTHORIZATION_DOCUMENT = "$AUTHORIZATION_API/authorization-documents"
const val AUTHORIZATION_GRANT = "$AUTHORIZATION_API/authorization-grants"
const val AUTHORIZATION_REQUEST = "$AUTHORIZATION_API/authorization-requests"
const val ID = "id"

fun Application.configureRouting() {
    routing {
        get(HEALTH) {
            call.respondText("OK", status = HttpStatusCode.OK)
        }
        route(AUTHORIZATION_DOCUMENT) {
            createDocumentRoute(get(), get())
            confirmDocumentRoute(get())
            getDocumentRoute(get())
        }
        route(AUTHORIZATION_GRANT) {
            getGrantRoute(get())
            getGrantScopesRoute(get())
            queryGrantsRoute(get())
        }
        route(AUTHORIZATION_REQUEST) {
            confirmRequestRoute(get())
            createRequestRoute(get(), get())
            getRequestRoute(get())
            queryRequestRoute(get())
        }
        swaggerUI(path = "openapi", swaggerFile = "openapi.yaml")
        staticResources("schemas/", "schemas")
    }
}
