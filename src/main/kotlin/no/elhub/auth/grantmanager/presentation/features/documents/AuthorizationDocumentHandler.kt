package no.elhub.auth.grantmanager.presentation.features.documents

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import no.elhub.auth.grantmanager.presentation.model.AuthorizationDocument
import no.elhub.auth.grantmanager.presentation.model.ResponseMeta
import java.util.*
import java.util.UUID

class AuthorizationDocumentHandler(
    private val signingService: SigningService
) {

    suspend fun getDocuments(call: ApplicationCall) {
        call.respond(status = HttpStatusCode.OK, message = ResponseMeta())
    }

    fun postDocument(authorizationDocumentRequest: PostAuthorizationDocumentRequest): AuthorizationDocument {
        val pdfBytes = PdfGenerator.createChangeOfSupplierConfirmationPdf(
            ssn = authorizationDocumentRequest.data.relationships.requestedTo.data.id,
            supplier = authorizationDocumentRequest.data.relationships.requestedBy.data.id,
            meteringPointId = authorizationDocumentRequest.data.attributes.meteringPoint
        )

        val signedPdf = signingService.addPadesSignature(pdfBytes)

        val authorizationDocument = AuthorizationDocument.Companion.of(authorizationDocumentRequest, signedPdf)
        AuthorizationDocumentRepository.insertDocument(authorizationDocument)
        return authorizationDocument
    }

    suspend fun getDocumentById(call: ApplicationCall) {
        call.respond(status = HttpStatusCode.OK, message = ResponseMeta())
    }

    fun getDocumentFile(documentId: UUID): ByteArray? = AuthorizationDocumentRepository.getDocumentFile(documentId)

    suspend fun patchDocumentById(call: ApplicationCall) {
        call.respond(status = HttpStatusCode.OK, message = ResponseMeta())
    }
}
