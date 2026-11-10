package no.elhub.auth.v1.api.documents.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.elhub.auth.v0.features.common.receiveEither
import no.elhub.auth.v0.features.common.toApiErrorResponse
import no.elhub.auth.v0.features.common.toTypeMismatchApiErrorResponse
import no.elhub.auth.v1.api.common.toApiErrorResponse
import no.elhub.auth.v1.api.common.toPartyIdentifier
import no.elhub.auth.v1.api.documents.request.JsonApiCreateAuthorizationDocumentRequest
import no.elhub.auth.v1.api.documents.request.toDocumentSpecification
import no.elhub.auth.v1.api.documents.response.toGetResponse
import no.elhub.auth.v1.common.auth.authorizedAgent
import no.elhub.auth.v1.domain.AgentExternalIdentifier
import no.elhub.auth.v1.features.documents.CreateDocumentHandler
import no.elhub.auth.v0.features.common.party.PartyService as AgentService

fun Route.route(
    agentService: AgentService,
    handler: CreateDocumentHandler,
) {
    post {
        val request =
            call.receiveEither<JsonApiCreateAuthorizationDocumentRequest>().fold(
                ifLeft = { error ->
                    val (status, body) = error.toApiErrorResponse()
                    call.respond(status, body)
                    return@post
                },
                ifRight = { it },
            )

        if (request.data.type != "AuthorizationDocument") {
            val (status, body) =
                toTypeMismatchApiErrorResponse(
                    expectedType = "AuthorizationDocument",
                    actualType = request.data.type,
                )
            call.respond(status, body)
            return@post
        }

        val specification =
            toDocumentSpecification(
                documentType = request.data.attributes.documentType,
                requestedScopes = request.data.attributes.requestedScopes,
                requestedGrantValidFrom = request.data.attributes.requestedGrantValidFrom,
                requestedGrantValidTo = request.data.attributes.requestedGrantValidTo,
            )

        val requestedPrincipalIdentifier =
            AgentExternalIdentifier
                .create(request.data.meta.requestedPrincipal)
                .toPartyIdentifier()
        val requestedPrincipal =
            agentService.resolve(requestedPrincipalIdentifier).fold(
                ifLeft = { error ->
                    call.respond(error.toApiErrorResponse())
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
                    call.respond(error.toApiErrorResponse())
                    return@post
                },
                ifRight = { it },
            )

        val requestedBy = call.authorizedAgent

        val document =
            handler.createAuthorizationDocument(
                specification = specification,
                externalReference = request.data.attributes.externalReference,
                requestedBy = requestedBy,
                requestedPrincipal = requestedPrincipal,
                requestedOf = requestedOf,
                language = request.data.meta.language,
            )
        call.respond(HttpStatusCode.Created, document.toGetResponse())
    }
}
