package no.elhub.auth.v1.api.documents.routes

import arrow.core.Either
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.elhub.auth.common.auth.authorizedParty
import no.elhub.auth.v0.features.common.party.PartyService
import no.elhub.auth.v0.features.common.receiveEither
import no.elhub.auth.v0.features.common.toApiErrorResponse
import no.elhub.auth.v0.features.common.toInternalServerApiErrorResponse
import no.elhub.auth.v0.features.common.toTypeMismatchApiErrorResponse
import no.elhub.auth.v0.features.common.toValidationApiErrorResponse
import no.elhub.auth.v1.api.common.toApiErrorResponse
import no.elhub.auth.v1.api.common.toPartyIdentifier
import no.elhub.auth.v1.api.documents.request.JsonApiCreateAuthorizationDocumentRequest
import no.elhub.auth.v1.api.documents.request.toDocumentSpecification
import no.elhub.auth.v1.api.documents.response.toGetResponse
import no.elhub.auth.v1.domain.AgentExternalIdentifier
import no.elhub.auth.v1.features.documents.CreateDocumentHandler
import no.elhub.auth.v1.features.documents.creation.DocumentPreparationError

fun Route.route(
    partyService: PartyService,
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
            partyService.resolve(requestedPrincipalIdentifier).fold(
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
            partyService.resolve(requestedOfIdentifier).fold(
                ifLeft = { error ->
                    call.respond(error.toApiErrorResponse())
                    return@post
                },
                ifRight = { it },
            )

        val requestedBy = call.authorizedParty

        when (
            val result = handler.createAuthorizationDocument(
                specification = specification,
                externalReference = request.data.attributes.externalReference,
                requestedBy = requestedBy,
                requestedPrincipal = requestedPrincipal,
                requestedOf = requestedOf,
                language = request.data.meta.language,
            )
        ) {
            is Either.Left -> {
                val (status, body) = when (result.value) {
                    DocumentPreparationError.RequestedMeteringPointsNotFound ->
                        toValidationApiErrorResponse("One or more requested metering points could not be found")

                    is DocumentPreparationError.MeteringPointsServiceFailure -> toInternalServerApiErrorResponse()
                }
                call.respond(status, body)
            }

            is Either.Right -> call.respond(HttpStatusCode.Created, result.value.toGetResponse())
        }
    }
}
