package no.elhub.auth.features.requests.create

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.util.url
import no.elhub.auth.features.common.ApiError
import no.elhub.auth.features.common.ApiErrorJson
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestProblem
import no.elhub.auth.features.requests.common.toResponseBody

fun Route.createRequestRoute(handler: CreateRequestHandler) {
    post {
        val payload = try {
            call.receive<HttpRequestBody>()
        } catch (badRequestException: BadRequestException) {
            val cause = badRequestException.cause
            when (cause) {
                is JsonConvertException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiErrorJson.from(
                            ApiError.BadRequest(detail = "Authorization request contains extra, unknown, or missing fields. "),
                            call.url(),
                        )
                    )
                }
            }
            return@post
        }

        val authorizationRequest = handler(payload.toDomainCommand())

        authorizationRequest.fold(
            ifLeft = { authRequestProblem ->
                when (authRequestProblem) {
                    is AuthorizationRequestProblem.NotFoundError,
                    is AuthorizationRequestProblem.DataBaseError,
                    is AuthorizationRequestProblem.UnexpectedError ->
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiErrorJson.from(
                                ApiError.InternalServerError(detail = "Unexpected error occurred during creating authorization request. "),
                                call.url(),
                            ),
                        )
                }
            },
            ifRight = { request ->
                call.respond(
                    status = HttpStatusCode.Created,
                    message = request.toResponseBody()
                )
            }
        )
    }
}
