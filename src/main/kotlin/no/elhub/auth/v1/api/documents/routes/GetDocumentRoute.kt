package no.elhub.auth.v1.api.documents.routes

import arrow.core.getOrElse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.acceptItems
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.Json
import no.elhub.auth.common.auth.authorizedParty
import no.elhub.auth.v0.config.jsonApiContentType
import no.elhub.auth.v0.features.common.toApiErrorResponse
import no.elhub.auth.v0.features.common.toNotAcceptedErrorResponse
import no.elhub.auth.v0.features.common.validatePathId
import no.elhub.auth.v1.api.documents.response.toGetResponse
import no.elhub.auth.v1.features.documents.GetDocumentHandler

fun Route.route(handler: GetDocumentHandler) {
    get("/{id}") {
        val id = validatePathId(call.parameters["id"])
            .getOrElse { error ->
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        val document = handler(id, call.authorizedParty)
        call.respond(HttpStatusCode.OK, document.toGetResponse())
    }

    get("/{id}.pdf") {
        val acceptedTypes = call.request.acceptItems()
        val acceptsPdf = acceptedTypes.any {
            ContentType.Application.Pdf.match(it.value) || ContentType.Any.match(it.value)
        }

        if (!acceptsPdf && acceptedTypes.isNotEmpty()) {
            val (status, body) = toNotAcceptedErrorResponse(
                detail = "Client must accept 'application/pdf' to receive the document as PDF",
            )
            call.respondText(Json.encodeToString(body), jsonApiContentType, status)
            return@get
        }

        val id = validatePathId(call.parameters["id"])
            .getOrElse { error ->
                val (status, body) = error.toApiErrorResponse()
                call.respondText(Json.encodeToString(body), jsonApiContentType, status)
                return@get
            }

        val document = handler(id, call.authorizedParty)
        call.respondBytes(document.pdfBytes, ContentType.Application.Pdf)
    }
}
