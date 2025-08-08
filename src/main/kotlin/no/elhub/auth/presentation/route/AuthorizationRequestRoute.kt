package no.elhub.auth.presentation.route

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.util.url
import no.elhub.auth.domain.request.AuthorizationRequestHandler
import no.elhub.auth.domain.request.AuthorizationRequestProblemById
import no.elhub.auth.domain.request.AuthorizationRequestProblemCreate
import no.elhub.auth.domain.request.AuthorizationRequestProblemList
import no.elhub.auth.presentation.jsonapi.PostAuthorizationRequestPayload
import no.elhub.auth.presentation.jsonapi.errors.ApiError
import no.elhub.auth.presentation.jsonapi.errors.ApiErrorJson
import no.elhub.auth.presentation.jsonapi.toGetAuthorizationRequestResponse
import no.elhub.auth.presentation.jsonapi.toGetAuthorizationRequestsResponse
import java.util.UUID
import no.elhub.auth.presentation.validation.validateId

fun Route.requests(requestHandler: AuthorizationRequestHandler) {
    get {
        requestHandler.getAllRequests().fold(
            ifLeft = { authRequestProblem ->
                when (authRequestProblem) {
                    is AuthorizationRequestProblemList.DataBaseError,
                    is AuthorizationRequestProblemList.UnexpectedError ->
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiErrorJson.from(
                                ApiError.InternalServerError(detail = "Unexpected error occurred during fetch authorization requests"),
                                call.url(),
                            ),
                        )
                }
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
        val id: UUID =
            validateId(call.parameters[ID]).getOrElse { error ->
                call.respond(HttpStatusCode.fromValue(error.status), ApiErrorJson.from(error, call.url()))
                return@get
            }

        requestHandler.getRequestById(id).fold(
            ifLeft = { authRequestProblem ->
                when (authRequestProblem) {
                    is AuthorizationRequestProblemById.NotFoundError ->
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiErrorJson.from(
                                ApiError.NotFound(detail = "Authorization request with id=$id not found"),
                                call.url(),
                            ),
                        )

                    is AuthorizationRequestProblemById.DataBaseError, AuthorizationRequestProblemById.UnexpectedError ->
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiErrorJson.from(
                                ApiError.InternalServerError(detail = "Unexpected error occurred during fetch authorization request with id=$id"),
                                call.url(),
                            ),
                        )
                }
            },
            ifRight = { request ->
                call.respond(
                    status = HttpStatusCode.OK,
                    message = request.toGetAuthorizationRequestResponse()
                )
            },
        )
    }

    post {
        val payload = try {
            call.receive<PostAuthorizationRequestPayload>()
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

        requestHandler.postRequest(payload).fold(
            ifLeft = { authRequestProblem ->
                when (authRequestProblem) {
                    is AuthorizationRequestProblemCreate.DataBaseError,
                    is AuthorizationRequestProblemCreate.UnexpectedError ->
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
                    message = request.toGetAuthorizationRequestResponse()
                )
            }
        )
    }
}
