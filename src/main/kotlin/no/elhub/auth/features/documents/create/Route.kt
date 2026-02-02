package no.elhub.auth.features.documents.create

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
import no.elhub.auth.features.common.toBalanceSupplierNotApiAuthorizedResponse
import no.elhub.auth.features.documents.create.dto.JsonApiCreateDocumentRequest
import no.elhub.auth.features.documents.create.dto.toCreateDocumentResponse
import no.elhub.auth.features.documents.create.dto.toModel
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(Route::class.java)

fun Route.route(
    handler: Handler,
    authProvider: AuthorizationProvider,
) {
    post {
        val resolvedActor =
            authProvider
                .authorizeMaskinporten(call)
                .getOrElse {
                    val error = it.toApiErrorResponse()
                    call.respond(error.first, error.second)
                    return@post
                }

        val requestBody = call.receive<JsonApiCreateDocumentRequest>()

        if (resolvedActor.role != RoleType.BalanceSupplier) {
            val (status, body) = toBalanceSupplierNotApiAuthorizedResponse()
            call.respond(status, body)
            return@post
        }

        val authorizedParty =
            AuthorizationParty(
                resourceId = resolvedActor.gln,
                type = PartyType.OrganizationEntity,
            )

        val model = requestBody.toModel(authorizedParty)

        val document =
            handler(model)
                .getOrElse { error ->
                    logger.error("Failed to create authorization document: {}", error)
                    val (status, validationError) = error.toApiErrorResponse()
                    call.respond(status, validationError)
                    return@post
                }

        logger.debug("Successfully created document {}", document.id)
        call.respond(
            status = HttpStatusCode.Created,
            message = document.toCreateDocumentResponse(),
        )
    }
}
