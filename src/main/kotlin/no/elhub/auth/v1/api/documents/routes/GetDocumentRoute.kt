package no.elhub.auth.v1.api.documents.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.acceptItems
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.common.respondError
import no.elhub.auth.v0.features.common.toNotAcceptedErrorResponse
import no.elhub.auth.v1.api.common.validatePathId
import no.elhub.auth.v1.api.common.validateQueryParameters
import no.elhub.auth.v1.api.documents.response.toGetResponse
import no.elhub.auth.v1.common.auth.authorizedAgent
import no.elhub.auth.v1.features.documents.GetDocumentHandler

fun Route.route(handler: GetDocumentHandler) {
    get("/{id}") {
        call.validateQueryParameters()
        val id = validatePathId(call.parameters["id"])

        val document = handler(id, call.authorizedAgent)
        call.respond(HttpStatusCode.OK, document.toGetResponse())
    }

    get("/{id}.pdf") {
        call.validateQueryParameters()
        val acceptedTypes = call.request.acceptItems()
        val acceptsPdf = acceptedTypes.any {
            ContentType.Application.Pdf.match(it.value) || ContentType.Any.match(it.value)
        }

        if (!acceptsPdf && acceptedTypes.isNotEmpty()) {
            val (status, body) = toNotAcceptedErrorResponse(
                detail = "Client must accept 'application/pdf' to receive the document as PDF",
            )
            call.respondError(status, body)
            return@get
        }

        val id = validatePathId(call.parameters["id"])

        val document = handler(id, call.authorizedAgent)
        call.respondBytes(document.pdfBytes, ContentType.Application.Pdf)
    }
}
