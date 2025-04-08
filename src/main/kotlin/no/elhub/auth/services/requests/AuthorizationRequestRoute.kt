package no.elhub.auth.services.requests

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.util.url
import no.elhub.auth.config.ID
import no.elhub.auth.model.AuthorizationRequest
import no.elhub.auth.model.ResponseMeta

private val logger = KotlinLogging.logger {}

fun Route.requestRoutes(requestService: AuthorizationRequestService) {
    get {
        call.respond(status = HttpStatusCode.OK, message = ResponseMeta())
    }
    post {
        try {
            val authRequest = call.receive<AuthorizationRequest.Request>()
            if (authRequest.data.attributes.requestType.isBlank()) {
                call.respondText("Missing or malformed request type", status = HttpStatusCode.BadRequest)
                return@post
            }
            logger.info { "Call service" }
            val newRequest = requestService.createRequest(authRequest)
            val response = AuthorizationRequest.Json(newRequest, selfLink = "${call.url()}/${newRequest.id}")
            call.respond(status = HttpStatusCode.Created, message = response)
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse request" }
            call.respondText("Malformed request", status = HttpStatusCode.BadRequest)
            return@post
        }
    }
    route("/$ID") {
        get {
            val id = call.parameters["id"]
            if (id == null) {
                call.respondText("Missing or malformed id", status = HttpStatusCode.BadRequest)
                return@get
            }
            val request = requestService.getRequest(id)
            val response = AuthorizationRequest.Json(request, selfLink = call.url())
            call.respond(status = HttpStatusCode.OK, message = response)
        }
    }
}
