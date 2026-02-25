package no.elhub.auth.features.documents.get

import arrow.core.getOrElse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.acceptItems
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.AuthorizedParty
import no.elhub.auth.features.common.auth.toApiErrorResponse
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.common.toNotAcceptedErrorResponse
import no.elhub.auth.features.common.validatePathId
import no.elhub.auth.features.documents.get.dto.toGetSingleResponse
import org.slf4j.LoggerFactory
import java.util.UUID

const val DOCUMENT_ID_PARAM = "id"
private val logger = LoggerFactory.getLogger(Route::class.java)

fun Route.route(handler: Handler, authProvider: AuthorizationProvider) {
    get("/{$DOCUMENT_ID_PARAM}") {
        val authorizedParty = authProvider.authorizeEndUserOrMaskinporten(call)
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        val id: UUID = validatePathId(call.parameters[DOCUMENT_ID_PARAM])
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        val query = when (authorizedParty) {
            is AuthorizedParty.OrganizationEntity -> Query(
                documentId = id,
                authorizedParty = AuthorizationParty(
                    id = authorizedParty.gln,
                    type = PartyType.OrganizationEntity
                )
            )

            is AuthorizedParty.Person -> Query(
                documentId = id,
                authorizedParty = AuthorizationParty(
                    id = authorizedParty.id.toString(),
                    type = PartyType.Person
                )
            )
        }

        val document = handler(query)
            .getOrElse { error ->
                logger.error("Failed to get authorization document: {}", error)
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        call.respond(
            status = HttpStatusCode.OK,
            message = document.toGetSingleResponse()
        )
    }

    get("/{$DOCUMENT_ID_PARAM}.pdf") {
        val acceptFromClient = call.request.acceptItems()
        val acceptsPdf = call.request.acceptItems().any {
            ContentType.Application.Pdf.match(it.value) || ContentType.Any.match(it.value)
        }

        if (!acceptsPdf && acceptFromClient.isNotEmpty()) {
            val (status, body) = toNotAcceptedErrorResponse(detail = "Client must accept 'application/pdf' to receive the document as PDF")
            call.respond(status, body)
            return@get
        }

        val authorizedParty = authProvider.authorizeEndUserOrMaskinporten(call)
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        val id: UUID = validatePathId(call.parameters[DOCUMENT_ID_PARAM])
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        val query = when (authorizedParty) {
            is AuthorizedParty.OrganizationEntity -> Query(
                documentId = id,
                authorizedParty = AuthorizationParty(
                    id = authorizedParty.gln,
                    type = PartyType.OrganizationEntity
                )
            )

            is AuthorizedParty.Person -> Query(
                documentId = id,
                authorizedParty = AuthorizationParty(
                    id = authorizedParty.id.toString(),
                    type = PartyType.Person
                )
            )
        }

        val document = handler(query)
            .getOrElse { error ->
                logger.error("Failed to get authorization document: {}", error)
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        call.respondBytes(
            bytes = document.file,
            contentType = ContentType.Application.Pdf
        )
    }
}
