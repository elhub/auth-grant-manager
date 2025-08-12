package no.elhub.auth.features.grants

import arrow.core.Either
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.elhub.auth.config.ID
import no.elhub.auth.features.errors.DomainError
import no.elhub.auth.features.errors.mapErrorToResponse
import no.elhub.auth.features.utils.validateId
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import toGetAuthorizationGrantScopeResponse

fun Route.grants(grantHandler: AuthorizationGrantHandler) {
    route("") {
        get {
            grantHandler.getAllGrants().fold(
                ifLeft = { authGrantProblem ->
                    val response = mapErrorToResponse(authGrantProblem)
                    call.respond(
                        status = HttpStatusCode.fromValue(response.status.toInt()),
                        message = JsonApiErrorCollection(listOf(response))
                    )
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
            call.withValidatedId(
                idParam = call.parameters[ID],
                validate = ::validateId
            ) { validatedId ->
                grantHandler.getGrantById(validatedId).fold(
                    ifLeft = { authGrantProblem ->
                        val response = mapErrorToResponse(authGrantProblem)
                        call.respond(
                            status = HttpStatusCode.fromValue(response.status.toInt()),
                            message = JsonApiErrorCollection(listOf(response))
                        )
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
                    }
                )
            }
        }

        get("/{$ID}/scopes") {
            call.withValidatedId(
                idParam = call.parameters[ID],
                validate = ::validateId
            ) { validatedId ->
                grantHandler.getGrantScopesById(validatedId).fold(
                    ifLeft = { authGrantProblem ->
                        val response = mapErrorToResponse(authGrantProblem)
                        call.respond(
                            status = HttpStatusCode.fromValue(response.status.toInt()),
                            message = JsonApiErrorCollection(listOf(response))
                        )
                    },
                    ifRight = { result ->
                        call.respond(
                            status = HttpStatusCode.OK,
                            message = result.toGetAuthorizationGrantScopeResponse()
                        )
                    }
                )
            }
        }
    }
}

// TODO -> this function is also used by AuthorizationRequestRoute
suspend fun <T> ApplicationCall.withValidatedId(
    idParam: String?,
    validate: (String?) -> Either<DomainError, T>,
    block: suspend (T) -> Unit
) {
    validate(idParam).fold(
        ifLeft = { error ->
            val response = mapErrorToResponse(error)
            respond(
                status = HttpStatusCode.fromValue(response.status.toInt()),
                message = JsonApiErrorCollection(listOf(response))
            )
        },
        ifRight = { value -> block(value) }
    )
}
