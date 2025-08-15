package no.elhub.auth.features.requests.query

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.util.url
import no.elhub.auth.features.common.ApiError
import no.elhub.auth.features.common.ApiErrorJson
import no.elhub.auth.features.requests.common.AuthorizationRequestProblem

fun Route.queryRequestRoute(handler: QueryRequestsHandler) {
    get {
        TODO("Build query from payload")
        handler(QueryRequestsQuery()).fold(
            ifLeft = { authRequestProblem ->
                when (authRequestProblem) {
                    is AuthorizationRequestProblem.NotFoundError,
                    is AuthorizationRequestProblem.DataBaseError,
                    is AuthorizationRequestProblem.UnexpectedError ->
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
                    message = requests.toResponseBody()
                )
            }
        )
    }
}
