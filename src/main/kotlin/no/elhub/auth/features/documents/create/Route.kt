package no.elhub.auth.features.documents.create

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.util.*

fun Route.createDocumentRoute(handler: CreateDocumentHandler) {
    post {
        val requestBody = call.receive<HttpRequestBody>()

        val authorizationDocument = handler(requestBody.toCreateDocumentCommand())

        call.respond(status = HttpStatusCode.Created, message = authorizationDocument.toResponseBody())
    }

}
