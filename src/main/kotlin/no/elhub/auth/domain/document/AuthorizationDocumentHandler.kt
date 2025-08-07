package no.elhub.auth.domain.document

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import no.elhub.auth.presentation.model.ResponseMeta
import java.util.UUID
import no.elhub.auth.data.exposed.repositories.AuthorizationDocumentRepository

class AuthorizationDocumentHandler(
    private val signingService: SigningService
) {

    suspend fun getDocuments(call: ApplicationCall) {
        call.respond(status = HttpStatusCode.OK, message = ResponseMeta())
    }

    fun createDocument(command: CreateAuthorizationDocumentCommand): AuthorizationDocument {
        val pdfBytes = PdfGenerator.createChangeOfSupplierConfirmationPdf(
            ssn = command.requestedTo,
            supplier = command.requestedBy,
            meteringPointId = command.meteringPoint
        )

        val signedPdf = signingService.addPadesSignature(pdfBytes)

        val authorizationDocument = AuthorizationDocument.of(command, signedPdf)
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
