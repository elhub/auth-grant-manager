package no.elhub.auth.features.grants.query

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.util.url
import no.elhub.auth.features.common.ApiError
import no.elhub.auth.features.grants.common.AuthorizationGrantProblem

fun Route.queryGrantsRoute(handler: QueryGrantsHandler) {
    get {

        TODO("Build query from payload")
        handler(QueryGrantsQuery()).fold(
            ifLeft = { authGrantProblem ->
                when (authGrantProblem) {
                    is AuthorizationGrantProblem.DataBaseError, AuthorizationGrantProblem.UnexpectedError,
                        ->
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiErrorJson.from(
                                ApiError.InternalServerError(detail = "Unexpected error occurred during fetch authorization grants"),
                                call.url(),
                            ),
                        )

                    is AuthorizationGrantProblem.NotFoundError ->
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiErrorJson.from(
                                ApiError.NotFound(detail = "Authorization grants not found"),
                                call.url(),
                            ),
                        )
                }
            },
            ifRight = { (grants, parties) ->
                call.respond(
                    status = HttpStatusCode.OK,
                    message = grants.toResponseBody { id ->
                        parties[id]
                            ?: throw RuntimeException("Party not found for id=$id") // respond w/ internalServerError if thrown
                    }
                )
            }
        )
    }
}
