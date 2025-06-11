package no.elhub.auth.features.grants

import arrow.core.Either.Left
import arrow.core.Either.Right
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.util.url
import no.elhub.auth.config.ID
import no.elhub.auth.features.errors.ApiError
import no.elhub.auth.features.errors.ApiErrorJson
import no.elhub.auth.features.utils.validateId

fun Route.grants(grantHandler: AuthorizationGrantHandler) {
    route("") {
        get {
            grantHandler.getAllGrants().fold(
                ifLeft = { authGrantProblem ->
                    when (authGrantProblem) {
                        is AuthorizationGrantProblem.DataBaseError, AuthorizationGrantProblem.InternalServerError,
                        AuthorizationGrantProblem.NullPointerError, AuthorizationGrantProblem.UnknownError,
                        ->
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ApiErrorJson.from(
                                    ApiError.InternalServerError(detail = "Internal error during fetching authorization grant"),
                                    call.url(),
                                ),
                            )

                        is AuthorizationGrantProblem.IllegalArgumentError ->
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ApiErrorJson.from(
                                    ApiError.InternalServerError(detail = "Bad request during fetching authorization grant"),
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
                ifRight = { grants ->
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = AuthorizationGrantResponseCollection.from(grants, call.url()),
                    )
                },
            )
        }

        get("/{$ID}") {
            val idResult = validateId(call.parameters[ID])
            when (idResult) {
                is Left -> {
                    call.respond(HttpStatusCode.fromValue(idResult.value.status), ApiErrorJson.from(idResult.value, call.url()))
                    return@get
                }
                is Right -> Unit // continue
            }

            val id = idResult.value
            grantHandler.getGrantById(id).fold(
                ifLeft = { authGrantProblem ->
                    when (authGrantProblem) {
                        is AuthorizationGrantProblem.NotFoundError ->
                            call.respond(
                                HttpStatusCode.NotFound,
                                ApiErrorJson.from(
                                    ApiError.NotFound(detail = "Authorization grant with id=$id not found"),
                                    call.url(),
                                ),
                            )

                        is AuthorizationGrantProblem.DataBaseError, AuthorizationGrantProblem.InternalServerError ->
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ApiErrorJson.from(
                                    ApiError.InternalServerError(detail = "Internal error during fetching authorization grant"),
                                    call.url(),
                                ),
                            )

                        is AuthorizationGrantProblem.IllegalArgumentError, AuthorizationGrantProblem.NullPointerError, AuthorizationGrantProblem.UnknownError ->
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ApiErrorJson.from(
                                    ApiError.InternalServerError(detail = "Internal error during fetching authorization grant"),
                                    call.url(),
                                ),
                            )
                    }
                },
                ifRight = { result ->
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = AuthorizationGrantResponse.from(result, selfLink = call.url()),
                    )
                },
            )
        }
    }
}
