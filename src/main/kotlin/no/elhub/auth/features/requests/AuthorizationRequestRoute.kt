package no.elhub.auth.features.requests

import arrow.core.Either.Left
import arrow.core.Either.Right
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
import no.elhub.auth.features.errors.ApiError
import no.elhub.auth.features.errors.ApiErrorJson

fun Route.requestRoutes(requestService: AuthorizationRequestHandler) {
    get {
        val result = requestService.getRequests()
        call.respond(status = HttpStatusCode.OK, message = AuthorizationRequestResponseCollection.from(result, call.url()))
    }
    post {
        val authRequest: AuthorizationRequestRequest
        try {
            authRequest = call.receive<AuthorizationRequestRequest>()
            if (authRequest.data.attributes.requestType.isBlank()) {
                call.respond(status = HttpStatusCode.BadRequest, message = ApiErrorJson.from(ApiError.BadRequest(detail = "Missing request type"), call.url()))
                return@post
            }
        } catch (e: Exception) {
            call.respond(status = HttpStatusCode.BadRequest, message = ApiErrorJson.from(ApiError.BadRequest(detail = e.message ?: ""), call.url()))
            return@post
        }
        val result = requestService.createRequest(authRequest)
        when (result) {
            is Left -> call.respond(HttpStatusCode.fromValue(result.value.status), ApiErrorJson.from(result.value, call.url()))
            is Right -> call.respond(status = HttpStatusCode.Created, message = AuthorizationRequestResponse.from(result.value, selfLink = call.url()))
        }
    }
    route("/$ID") {
        get {
            val id = call.parameters["id"]
            if (id == null) {
                call.respondText("Missing or malformed id", status = HttpStatusCode.BadRequest)
                return@get
            }
            val result = requestService.getRequest(id)
            when (result) {
                is Left -> call.respond(HttpStatusCode.fromValue(result.value.status), ApiErrorJson.from(result.value, call.url()))
                is Right -> call.respond(status = HttpStatusCode.OK, message = AuthorizationRequestResponse.from(result.value, selfLink = call.url()))
            }
        }
    }
}
