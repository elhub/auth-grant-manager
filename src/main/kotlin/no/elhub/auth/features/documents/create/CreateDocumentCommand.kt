package no.elhub.auth.features.documents.create

import arrow.core.Either
import no.elhub.auth.features.documents.AuthorizationDocument
import java.time.LocalDateTime
import java.util.UUID

sealed interface CreateDocumentCommand {
    data class ChangeOfSupplier(
        val requestedFrom: String,
        val requestedFromName: String,
        val requestedBy: String,
        val balanceSupplierContractName: String,
        val meteringPointId: String,
        val meteringPointAddress: String,

        ) : CreateDocumentCommand

}

fun CreateDocumentCommand.toAuthorizationDocument(
    pdfBytes: PdfBytes
): Either<CreateDocumentError.MappingError, AuthorizationDocument> =
    Either.catch {
        when (this) {
            is CreateDocumentCommand.ChangeOfSupplier ->
                AuthorizationDocument(
                    id = UUID.randomUUID(),
                    title = "Change of supplier confirmation",
                    pdfBytes = pdfBytes,
                    type = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                    status = AuthorizationDocument.Status.Pending,
                    requestedBy = this.requestedBy,
                    // NOTE: your original code used requestedTo, but the command has requestedFrom.
                    // We map requestedFrom -> requestedTo here.
                    requestedTo = this.requestedFrom,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
        }
    }.mapLeft { CreateDocumentError.MappingError }
