package no.elhub.auth.presentation.route

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.util.url
import no.elhub.auth.domain.grant.AuthorizationGrantHandler
import no.elhub.auth.domain.grant.AuthorizationGrantProblem
import no.elhub.auth.presentation.jsonapi.errors.ApiError
import no.elhub.auth.presentation.jsonapi.errors.ApiErrorJson
import no.elhub.auth.presentation.jsonapi.toGetAuthorizationGrantResponse
import no.elhub.auth.presentation.jsonapi.toGetAuthorizationGrantScopeResponse
import no.elhub.auth.presentation.jsonapi.toGetAuthorizationGrantsResponse
import java.util.UUID
import no.elhub.auth.presentation.validation.validateId

fun Route.grants(grantHandler: AuthorizationGrantHandler) {
    route("") {
        get {
            grantHandler.getAllGrants().fold(
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
                        message = grants.toGetAuthorizationGrantsResponse { id ->
                            parties[id] ?: throw RuntimeException("Party not found for id=$id") // respond w/ internalServerError if thrown
                        }
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

                        is AuthorizationGrantProblem.DataBaseError, AuthorizationGrantProblem.UnexpectedError ->
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ApiErrorJson.from(
                                    ApiError.InternalServerError(detail = "Unexpected error occurred during fetch authorization grant with id=$id"),
                                    call.url(),
                                ),
                            )
                    }
                },
                ifRight = { (grant, parties) ->
                    if (grant != null) {
                        call.respond(
                            status = HttpStatusCode.OK,
                            message = grant.toGetAuthorizationGrantResponse { id ->
                                parties[id]
                                    ?: throw RuntimeException("Party not found for id=$id")
                            }
                        )
                    }
                },
            )
        }

        get("/{$ID}/scopes") {
            val id: UUID =
                validateId(call.parameters[ID]).getOrElse { error ->
                    call.respond(HttpStatusCode.fromValue(error.status), ApiErrorJson.from(error, call.url()))
                    return@get
                }

            grantHandler.getGrantScopesById(id).fold(
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
                        message = result.toGetAuthorizationGrantScopeResponse()
                    )
                },
            )
        }
    }
}
