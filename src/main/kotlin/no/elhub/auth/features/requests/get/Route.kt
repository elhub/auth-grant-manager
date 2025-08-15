package no.elhub.auth.features.requests.get

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.util.url
import no.elhub.auth.config.ID
import no.elhub.auth.features.common.ApiError
import no.elhub.auth.features.common.ApiErrorJson
import no.elhub.auth.features.common.validateId
import no.elhub.auth.features.requests.common.AuthorizationRequestProblem
import no.elhub.auth.features.requests.common.toResponseBody
import java.util.UUID

fun Route.getRequestRoute(handler: GetRequestHandler) {
    get("/{$ID}") {
        val id: UUID =
            validateId(call.parameters[ID]).getOrElse { error ->
                call.respond(HttpStatusCode.fromValue(error.status), ApiErrorJson.from(error, call.url()))
                return@get
            }

        handler(GetRequestQuery(id)).fold(
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
                    message = request.toResponseBody()
                )
            },
        )
    }
}
