package no.elhub.auth.v1.features.documents.create

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.elhub.auth.v0.features.common.auth.authorizedParty
import no.elhub.auth.v0.features.common.buildApiErrorResponse
import no.elhub.auth.v0.features.common.party.PartyError
import no.elhub.auth.v0.features.common.party.PartyService
import no.elhub.auth.v0.features.common.toTypeMismatchApiErrorResponse
import no.elhub.auth.v1.features.documents.create.dto.JsonApiCreateAuthorizationDocumentRequest
import no.elhub.auth.v1.features.documents.create.dto.toCreateResponse

fun Route.route(
    partyService: PartyService,
    handler: CreateDocumentBusinessHandler,
    payloadValidator: CreateAuthorizationDocumentPayloadValidator,
) {
    post {
        val request = try {
            call.receive<JsonApiCreateAuthorizationDocumentRequest>()
        } catch (exception: BadRequestException) {
            val (status, body) = buildApiErrorResponse(
                status = HttpStatusCode.BadRequest,
                title = "Invalid request body",
                detail = exception.message ?: "The request body could not be deserialized",
            )
            call.respond(status, body)
            return@post
        }

        if (request.data.type != "AuthorizationDocument") {
            val (status, body) = toTypeMismatchApiErrorResponse(
                expectedType = "AuthorizationDocument",
                actualType = request.data.type,
            )
            call.respond(status, body)
            return@post
        }

        val requestedScope = payloadValidator.validate(request)

        val requestedFrom = partyService.resolve(request.data.meta.requestedFrom).fold(
            ifLeft = { error ->
                call.respond(error.toApiErrorResponse())
                return@post
            },
            ifRight = { it },
        )

        val requestedTo = partyService.resolve(request.data.meta.requestedTo).fold(
            ifLeft = { error ->
                call.respond(error.toApiErrorResponse())
                return@post
            },
            ifRight = { it },
        )

        val requestedBy = call.authorizedParty

        val document = handler.createAuthorizationDocument(
            requestedScope = requestedScope,
            externalReference = request.data.attributes.externalReference,
            requestedBy = requestedBy,
            requestedFrom = requestedFrom,
            requestedTo = requestedTo,
            language = request.data.meta.language,
        )
        call.respond(HttpStatusCode.Created, document.toCreateResponse(request.data.meta.language))
    }
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
