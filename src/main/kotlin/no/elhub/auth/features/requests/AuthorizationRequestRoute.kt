package no.elhub.auth.features.requests

import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.flatMap
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.util.url
import no.elhub.auth.config.ID
import no.elhub.auth.features.errors.ApiError
import no.elhub.auth.features.errors.ApiErrorJson
import no.elhub.auth.features.utils.validateAuthorizationRequest
import no.elhub.auth.features.utils.validateId

fun Route.requestRoutes(requestService: AuthorizationRequestHandler) {

    get {
        val result = requestService.getRequests()
        call.respond(status = HttpStatusCode.OK, message = AuthorizationRequestResponseCollection.from(result, call.url()))
    }

    post {
        val authRequestResult = Either.catch {
            call.receive<AuthorizationRequestRequest>()
        }.mapLeft {
            ApiError.BadRequest(detail = "Missing or malformed requestBody in POST call.")
        }.flatMap {
            validateAuthorizationRequest(it)
        }
        when (authRequestResult) {
            is Left -> {
                call.respond(HttpStatusCode.fromValue(authRequestResult.value.status), ApiErrorJson.from(authRequestResult.value, call.url()))
                return@post
            }
            is Right -> Unit // continue
        }
        val result = requestService.createRequest(authRequestResult.value)
        when (result) {
            is Left -> call.respond(HttpStatusCode.fromValue(result.value.status), ApiErrorJson.from(result.value, call.url()))
            is Right -> call.respond(status = HttpStatusCode.Created, message = AuthorizationRequestResponse.from(result.value, selfLink = call.url()))
        }
    }

    route("/{$ID}") {
        get {
            val idResult = validateId(call.parameters[ID])
            when (idResult) {
                is Left -> {
                    call.respond(HttpStatusCode.fromValue(idResult.value.status), ApiErrorJson.from(idResult.value, call.url()))
                    return@get
                }
                is Right -> Unit // continue
            }
            val result = requestService.getRequest(idResult.value)
            when (result) {
                is Left -> {
                    call.respond(HttpStatusCode.fromValue(result.value.status), ApiErrorJson.from(result.value, call.url()))
                }
                is Right -> call.respond(status = HttpStatusCode.OK, message = AuthorizationRequestResponse.from(result.value, selfLink = call.url()))
            }
        }
    }

}

