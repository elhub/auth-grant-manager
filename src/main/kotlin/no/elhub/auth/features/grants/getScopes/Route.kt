package no.elhub.auth.features.grants.getScopes

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.util.url
import no.elhub.auth.features.common.validateId
import no.elhub.auth.config.ID
import no.elhub.auth.features.common.ApiError
import no.elhub.auth.features.grants.common.AuthorizationGrantProblem
import no.elhub.auth.features.grants.common.toResponseBody
import java.util.UUID

fun Route.getGrantScopesRoute(handler: GetGrantScopesHandler) {
    route("/{$ID}/scopes") {
        get {
            val id: UUID =
                validateId(call.parameters[ID]).getOrElse { error ->
                    call.respond(HttpStatusCode.fromValue(error.status), ApiErrorJson.from(error, call.url()))
                    return@get
                }

            handler(GetGrantScopesQuery(id)).fold(
                ifLeft = { authGrantProblem ->
                    when (authGrantProblem) {
                        is AuthorizationGrantProblem.NotFoundError ->
                            call.respond(
                                HttpStatusCode.NotFound,
                                ApiErrorJson.from(
                                    ApiError.NotFound(detail = "Authorization scope for grant with id=$id not found"),
                                    call.url(),
                                ),
                            )

                        is AuthorizationGrantProblem.DataBaseError, AuthorizationGrantProblem.UnexpectedError ->
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ApiErrorJson.from(
                                    ApiError.InternalServerError(
                                        detail = "Unexpected error occurred during fetch authorization scopes " +
                                                "for authorization grant with id=$id"
                                    ),
                                    call.url(),
                                ),
                            )
                    }
                },
                ifRight = { result ->
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = result.toResponseBody()
                    )
                },
            )
        }
    }
}
