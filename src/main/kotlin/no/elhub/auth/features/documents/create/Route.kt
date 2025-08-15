package no.elhub.auth.features.documents.create

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.RoutingHandler
import io.ktor.server.routing.post
import no.elhub.auth.features.documents.AuthorizationDocument
import java.util.*

fun Route.createDocumentRoute(handler: CreateDocumentHandler) {
    post {
        val requestBody = call.receive<HttpRequestBody>()

        val authorizationDocument = handler(requestBody.toDomainCommand())

        call.respond(status = HttpStatusCode.Created, message = authorizationDocument.toResponseBody())
    }

}
