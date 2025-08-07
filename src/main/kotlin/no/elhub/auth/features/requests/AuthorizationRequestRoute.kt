package no.elhub.auth.features.requests

import arrow.core.raise.either
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.elhub.auth.config.ID
import no.elhub.auth.features.errors.DomainError
import no.elhub.auth.features.errors.mapErrorToResponse
import no.elhub.auth.features.utils.validateIdWithDomainError
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

fun Route.requests(requestHandler: AuthorizationRequestHandler) {
    get {
        either {
            val requests = requestHandler.getAllRequests().bind()
            call.respond(
                HttpStatusCode.OK,
                message = requests.toGetAuthorizationRequestsResponse()
            )
        }.mapLeft { error ->
            val response = mapErrorToResponse(error)
            call.respond(
                status = HttpStatusCode.fromValue(response.status.toInt()),
                message = JsonApiErrorCollection(listOf(response))
            )
        }
    }

    get("/{$ID}") {
        either {
            val id = validateIdWithDomainError(call.parameters[ID]).bind()
            val request = requestHandler.getRequestById(id).bind()
            call.respond(
                status = HttpStatusCode.OK,
                message = request.toGetAuthorizationRequestResponse()
            )
        }.mapLeft { error ->
            val response = mapErrorToResponse(error)
            call.respond(
                status = HttpStatusCode.fromValue(response.status.toInt()),
                message = JsonApiErrorCollection(listOf(response))
            )
        }
    }

    post {
        either {
            val payload = runCatching { call.receive<PostAuthorizationRequestPayload>() }.getOrElse { exception ->
                val response = mapErrorToResponse(DomainError.ApiError.AuthorizationPayloadInvalid(exception))
                call.respond(
                    status = HttpStatusCode.fromValue(response.status.toInt()),
                    message = JsonApiErrorCollection(listOf(response))
                )
                return@post
            }

            val response = requestHandler.postRequest(payload).bind()
            call.respond(
                status = HttpStatusCode.Created,
                message = response.toGetAuthorizationRequestResponse()
            )
        }.mapLeft { error ->
            val response = mapErrorToResponse(error)
            call.respond(
                status = HttpStatusCode.fromValue(response.status.toInt()),
                message = JsonApiErrorCollection(listOf(response))
            )
        }
    }
}
