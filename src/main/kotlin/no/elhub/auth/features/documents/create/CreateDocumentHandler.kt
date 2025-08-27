package no.elhub.auth.features.documents.create

import java.time.LocalDateTime
import java.util.UUID
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository

class CreateDocumentHandler(
    private val documentGenerator: DocumentGenerator,
    private val signingService: SigningService,
    private val repo: DocumentRepository
) {
    operator fun invoke(command: CreateDocumentCommand): AuthorizationDocument {
        val pdfBytes = documentGenerator.generateDocument(
            ssn = command.requestedTo,
            supplier = command.requestedBy,
            meteringPointId = command.meteringPoint
        )

        val signedPdf = signingService.addPadesSignature(pdfBytes)

        val authorizationDocument = command.toAuthorizationDocument(signedPdf)
        repo.insert(authorizationDocument)
        return authorizationDocument
    }
}

fun CreateDocumentCommand.toAuthorizationDocument(pdfBytes: ByteArray) =
    AuthorizationDocument(
        id = UUID.randomUUID(),
        title = "Title",
        pdfBytes = pdfBytes,
        type = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
        status = AuthorizationDocument.Status.Pending,
        requestedBy = this.requestedBy,
        requestedTo = this.requestedTo,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

