package no.elhub.auth.domain.document

import java.util.UUID

class AuthorizationDocumentHandler(
    private val signingService: SigningService,
    private val documentRepository: DocumentRepository
) {

    fun createDocument(command: CreateAuthorizationDocumentCommand): AuthorizationDocument {
        val pdfBytes = PdfGenerator.createChangeOfSupplierConfirmationPdf(
            ssn = command.requestedTo,
            supplier = command.requestedBy,
            meteringPointId = command.meteringPoint
        )

        val signedPdf = signingService.addPadesSignature(pdfBytes)

        val authorizationDocument = AuthorizationDocument.of(command, signedPdf)
        documentRepository.insertDocument(authorizationDocument)
        return authorizationDocument
    }

    fun getDocumentFile(documentId: UUID): ByteArray? = documentRepository.getDocumentFile(documentId)
}
