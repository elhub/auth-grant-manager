package no.elhub.auth.features.requests

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.elhub.auth.config.ID
import no.elhub.auth.features.errors.ApiError
import no.elhub.auth.features.errors.mapErrorToResponse
import no.elhub.auth.features.grants.withValidatedId
import no.elhub.auth.features.utils.validateId
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

fun Route.requests(requestHandler: AuthorizationRequestHandler) {
    get {
        requestHandler.getAllRequests().fold(
            ifLeft = { authRequestProblem ->
                val response = mapErrorToResponse(authRequestProblem)
                call.respond(
                    status = HttpStatusCode.fromValue(response.status.toInt()),
                    message = JsonApiErrorCollection(listOf(response))
                )
            },
            ifRight = { requests ->
                call.respond(
                    HttpStatusCode.OK,
                    message = requests.toGetAuthorizationRequestsResponse()
                )
            }
        )
    }

    get("/{$ID}") {
        call.withValidatedId(
            idParam = call.parameters[ID],
            validate = ::validateId
        ) { validatedId ->
            requestHandler.getRequestById(validatedId).fold(
                ifLeft = { authRequestProblem ->
                    val response = mapErrorToResponse(authRequestProblem)
                    call.respond(
                        status = HttpStatusCode.fromValue(response.status.toInt()),
                        message = JsonApiErrorCollection(listOf(response))
                    )
                },
                ifRight = { request ->
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = request.toGetAuthorizationRequestResponse()
                    )
                }
            )
        }
    }

    post {
        val payload = runCatching { call.receive<PostAuthorizationRequestPayload>() }.getOrElse { exception ->
            val response = mapErrorToResponse(ApiError.AuthorizationPayloadInvalid(exception))
            call.respond(
                status = HttpStatusCode.fromValue(response.status.toInt()),
                message = JsonApiErrorCollection(listOf(response))
            )
            return@post
        }

        requestHandler.postRequest(payload).fold(
            ifLeft = { authRequestProblem ->
                val response = mapErrorToResponse(authRequestProblem)
                call.respond(
                    status = HttpStatusCode.fromValue(response.status.toInt()),
                    message = JsonApiErrorCollection(listOf(response))
                )
            },
            ifRight = { request ->
                call.respond(
                    status = HttpStatusCode.Created,
                    message = request.toGetAuthorizationRequestResponse()
                )
            }
        )
    }
}
