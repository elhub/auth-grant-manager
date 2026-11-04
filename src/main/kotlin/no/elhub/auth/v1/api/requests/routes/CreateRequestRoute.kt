package no.elhub.auth.v1.api.requests.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.elhub.auth.common.auth.authorizedParty
import no.elhub.auth.v0.features.common.party.PartyService
import no.elhub.auth.v0.features.common.receiveEither
import no.elhub.auth.v0.features.common.toApiErrorResponse
import no.elhub.auth.v0.features.common.toTypeMismatchApiErrorResponse
import no.elhub.auth.v1.api.common.toApiErrorResponse
import no.elhub.auth.v1.api.common.toPartyIdentifier
import no.elhub.auth.v1.api.requests.request.JsonApiCreateAuthorizationRequestRequest
import no.elhub.auth.v1.api.requests.request.validateAndReturnAuthorizationRequestSpecification
import no.elhub.auth.v1.api.requests.response.toGetResponse
import no.elhub.auth.v1.domain.AgentExternalIdentifier
import no.elhub.auth.v1.features.requests.CreateRequestHandler

fun Route.route(
    partyService: PartyService,
    handler: CreateRequestHandler,
) {
    post {
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
            partyService.resolve(requestedPrincipalIdentifier).fold(
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
            partyService.resolve(requestedOfIdentifier).fold(
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
                requestedBy = call.authorizedParty,
                requestedPrincipal = requestedPrincipal,
                requestedOf = requestedOf,
            )
        call.respond(HttpStatusCode.Created, authorizationRequest.toGetResponse())
    }
}
