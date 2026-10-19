package no.elhub.auth.v1.api.documents.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.elhub.auth.v0.features.common.auth.authorizedParty
import no.elhub.auth.v0.features.common.buildApiErrorResponse
import no.elhub.auth.v0.features.common.party.PartyError
import no.elhub.auth.v0.features.common.party.PartyIdentifier
import no.elhub.auth.v0.features.common.party.PartyIdentifierType
import no.elhub.auth.v0.features.common.party.PartyService
import no.elhub.auth.v0.features.common.receiveEither
import no.elhub.auth.v0.features.common.toApiErrorResponse
import no.elhub.auth.v0.features.common.toTypeMismatchApiErrorResponse
import no.elhub.auth.v1.api.documents.request.JsonApiCreateAuthorizationDocumentRequest
import no.elhub.auth.v1.api.documents.request.toDocumentSpecification
import no.elhub.auth.v1.api.documents.response.toGetResponse
import no.elhub.auth.v1.domain.AgentExternalIdentifier
import no.elhub.auth.v1.features.documents.CreateDocumentHandler

fun Route.route(
    partyService: PartyService,
    handler: CreateDocumentHandler,
) {
    post {
        val request = call.receiveEither<JsonApiCreateAuthorizationDocumentRequest>().fold(
            ifLeft = { error ->
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@post
            },
            ifRight = { it },
        )

        if (request.data.type != "AuthorizationDocument") {
            val (status, body) = toTypeMismatchApiErrorResponse(
                expectedType = "AuthorizationDocument",
                actualType = request.data.type,
            )
            call.respond(status, body)
            return@post
        }

        val specification = toDocumentSpecification(
            documentType = request.data.attributes.documentType,
            requestedScopes = request.data.attributes.requestedScopes,
        )

        val requestedPrincipalIdentifier = AgentExternalIdentifier.create(request.data.meta.requestedPrincipal)
            .toPartyIdentifier()
        val requestedPrincipal = partyService.resolve(requestedPrincipalIdentifier).fold(
            ifLeft = { error ->
                call.respond(error.toApiErrorResponse())
                return@post
            },
            ifRight = { it },
        )

        val requestedOfIdentifier = AgentExternalIdentifier.create(request.data.meta.requestedOf)
            .toPartyIdentifier()
        val requestedOf = partyService.resolve(requestedOfIdentifier).fold(
            ifLeft = { error ->
                call.respond(error.toApiErrorResponse())
                return@post
            },
            ifRight = { it },
        )

        val requestedBy = call.authorizedParty

        val document = handler.createAuthorizationDocument(
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

private fun AgentExternalIdentifier.toPartyIdentifier() = when (this) {
    is AgentExternalIdentifier.OrganizationIdentifier ->
        PartyIdentifier(PartyIdentifierType.OrganizationNumber, value)

    is AgentExternalIdentifier.PersonIdentifier ->
        PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, value)

    is AgentExternalIdentifier.MarketPartyIdentifier ->
        PartyIdentifier(PartyIdentifierType.GlobalLocationNumber, value)
}

private fun PartyError.toApiErrorResponse() = when (this) {
    PartyError.InvalidNin -> buildApiErrorResponse(
        status = HttpStatusCode.BadRequest,
        title = "Invalid national identity number",
        detail = "Provided national identity number is invalid",
    )

    PartyError.PersonResolutionError -> buildApiErrorResponse(
        status = HttpStatusCode.UnprocessableEntity,
        title = "Unable to resolve party",
        detail = "The requested party could not be resolved",
    )
}
