package no.elhub.auth.features.requests

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
import no.elhub.auth.config.ID
import no.elhub.auth.features.errors.ApiError
import no.elhub.auth.features.errors.ApiErrorJson
import no.elhub.auth.features.utils.validateId
import java.util.UUID

fun Route.requests(requestHandler: AuthorizationRequestHandler) {
    get {
        requestHandler.getAllRequests().fold(
            ifLeft = { authRequestProblem ->
                when (authRequestProblem) {
                    is AuthorizationRequestProblem.DataBaseError,
                    is AuthorizationRequestProblem.UnexpectedError ->
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiErrorJson.from(
                                ApiError.InternalServerError(detail = "Unexpected error occurred during fetch authorization requests"),
                                call.url(),
                            ),
                        )
                    AuthorizationRequestProblem.NotFoundError -> error("NotFoundError should never be returned for findAll()")
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
                    is AuthorizationRequestProblem.NotFoundError ->
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiErrorJson.from(
                                ApiError.NotFound(detail = "Authorization request with id=$id not found"),
                                call.url(),
                            ),
                        )

                    is AuthorizationRequestProblem.DataBaseError, AuthorizationRequestProblem.UnexpectedError ->
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
                            ApiError.BadRequest(detail = "Authorization request contains extra, unknown, or missing fields."),
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
                    is AuthorizationRequestProblem.NotFoundError ->
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiErrorJson.from(
                                ApiError.NotFound(detail = "Authorization request was not inserted correctly into the database. "),
                                call.url(),
                            ),
                        )

                    is AuthorizationRequestProblem.DataBaseError, AuthorizationRequestProblem.UnexpectedError ->
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiErrorJson.from(
                                ApiError.InternalServerError(detail = "Unexpected error occurred during insert authorization request. "),
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
