package no.elhub.auth.v1.api.requests.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.elhub.auth.v0.features.common.receiveEither
import no.elhub.auth.v0.features.common.toApiErrorResponse
import no.elhub.auth.v0.features.common.toTypeMismatchApiErrorResponse
import no.elhub.auth.v1.api.common.toApiErrorResponse
import no.elhub.auth.v1.api.common.toPartyIdentifier
import no.elhub.auth.v1.api.common.validateQueryParameters
import no.elhub.auth.v1.api.requests.request.JsonApiCreateAuthorizationRequestRequest
import no.elhub.auth.v1.api.requests.request.validateAndReturnAuthorizationRequestSpecification
import no.elhub.auth.v1.api.requests.response.toGetResponse
import no.elhub.auth.v1.common.auth.authorizedAgent
import no.elhub.auth.v1.domain.AgentExternalIdentifier
import no.elhub.auth.v1.features.requests.CreateRequestHandler
import no.elhub.auth.v0.features.common.party.PartyService as AgentService

fun Route.route(
    agentService: AgentService,
    handler: CreateRequestHandler,
) {
    post {
        call.validateQueryParameters()

        val request =
            call.receiveEither<JsonApiCreateAuthorizationRequestRequest>().fold(
                ifLeft = { error ->
                    val (status, body) = error.toApiErrorResponse()
                    call.respond(status, body)
                    return@post
                },
                ifRight = { it },
            )

        if (request.data.type != "AuthorizationRequest") {
            val (status, body) =
                toTypeMismatchApiErrorResponse(
                    expectedType = "AuthorizationRequest",
                    actualType = request.data.type,
                )
            call.respond(status, body)
            return@post
        }

        val specification = request.data.attributes.validateAndReturnAuthorizationRequestSpecification()

        val requestedPrincipalIdentifier =
            AgentExternalIdentifier
                .create(request.data.meta.requestedPrincipal)
                .toPartyIdentifier()
        val requestedPrincipal =
            agentService.resolve(requestedPrincipalIdentifier).fold(
                ifLeft = { error ->
                    val (status, body) = error.toApiErrorResponse()
                    call.respond(status, body)
                    return@post
                },
                ifRight = { it },
            )

        val requestedOfIdentifier =
            AgentExternalIdentifier
                .create(request.data.meta.requestedOf)
                .toPartyIdentifier()
        val requestedOf =
            agentService.resolve(requestedOfIdentifier).fold(
                ifLeft = { error ->
                    val (status, body) = error.toApiErrorResponse()
                    call.respond(status, body)
                    return@post
                },
                ifRight = { it },
            )

        val authorizationRequest =
            handler(
                specification = specification,
                requestedBy = call.authorizedAgent,
                requestedPrincipal = requestedPrincipal,
                requestedOf = requestedOf,
            )
        call.respond(HttpStatusCode.Created, authorizationRequest.toGetResponse())
    }
}
