package no.elhub.auth.features.grants

import arrow.core.raise.either
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.elhub.auth.config.ID
import no.elhub.auth.features.errors.mapErrorToResponse
import no.elhub.auth.features.utils.validateId
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import toGetAuthorizationGrantScopeResponse

fun Route.grants(grantHandler: AuthorizationGrantHandler) {
    route("") {
        get {
            either {
                val result = grantHandler.getAllGrants().bind()
                call.respond(
                    status = HttpStatusCode.OK,
                    message = result.grants.toGetAuthorizationGrantsResponse { id ->
                        // repository should guarantee all parties are present
                        result.parties[id]!!
                    }
                )
            }.mapLeft { error ->
                val response = mapErrorToResponse(error)
                call.respond(
                    status = HttpStatusCode.fromValue(response.status.toInt()),
                    message = JsonApiErrorCollection(listOf(response))
                )
            }
        }

        get("/{$ID}") {
            either {
                val id = validateId(call.parameters[ID]).bind()
                val result = grantHandler.getGrantById(id).bind()
                result.grant?.let {
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = it.toGetAuthorizationGrantResponse { id ->
                            // repository should guarantee all parties are present
                            result.parties[id]!!
                        }
                    )
                }
            }.mapLeft { error ->
                val response = mapErrorToResponse(error)
                call.respond(
                    status = HttpStatusCode.fromValue(response.status.toInt()),
                    message = JsonApiErrorCollection(listOf(response))
                )
            }
        }

        get("/{$ID}/scopes") {
            either {
                val id = validateId(call.parameters[ID]).bind()
                val scope = grantHandler.getGrantScopesById(id).bind()
                call.respond(
                    status = HttpStatusCode.OK,
                    message = scope.toGetAuthorizationGrantScopeResponse()
                )
            }.mapLeft { error ->
                val response = mapErrorToResponse(error)
                call.respond(
                    status = HttpStatusCode.fromValue(response.status.toInt()),
                    message = JsonApiErrorCollection(listOf(response))
                )
            }
        }
    }
}
