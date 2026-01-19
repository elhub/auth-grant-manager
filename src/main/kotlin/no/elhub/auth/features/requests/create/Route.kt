package no.elhub.auth.features.requests.create

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.RoleType
import no.elhub.auth.features.common.auth.toApiErrorResponse
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.requests.create.dto.JsonApiCreateRequest
import no.elhub.auth.features.requests.create.dto.toCreateResponse
import no.elhub.auth.features.requests.create.dto.toModel
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(Route::class.java)

fun Route.route(
    handler: Handler,
    authProvider: AuthorizationProvider
) {
    post {
        val resolvedActor = authProvider.authorizeMaskinporten(call)
            .getOrElse {
                val error = it.toApiErrorResponse()
                call.respond(error.first, error.second)
                return@post
            }

        val requestBody = call.receive<JsonApiCreateRequest>()
        if (resolvedActor.role != RoleType.BalanceSupplier) {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }

        val authorizedParty = AuthorizationParty(
            resourceId = resolvedActor.gln,
            type = PartyType.OrganizationEntity
        )

        val request =
            handler(requestBody.toModel(authorizedParty))
                .getOrElse { error ->
                    logger.error("Failed to create authorization request: {}", error)
                    when (error) {
                        is
                        CreateRequestError.MappingError,
                        CreateRequestError.PersistenceError,
                        CreateRequestError.RequestedByPartyError,
                        CreateRequestError.RequestedFromPartyError,
                        -> call.respond(HttpStatusCode.InternalServerError)

                        CreateRequestError.AuthorizationError -> {
                            call.respond(
                                status = HttpStatusCode.Forbidden,
                                message = JsonApiErrorCollection(
                                    listOf(
                                        JsonApiErrorObject(
                                            status = HttpStatusCode.Forbidden.toString(),
                                            code = "not_authorized",
                                            title = "Party Not Authorized",
                                            detail = "RequestedBy must match the authorized party",
                                        )
                                    )
                                )
                            )
                        }

                        is CreateRequestError.ValidationError -> {
                            val (status, validationError) = error.toApiErrorResponse()
                            call.respond(status, validationError)
                        }
                    }
                    return@post
                }

        call.respond(HttpStatusCode.Created, request.toCreateResponse())
    }
}
