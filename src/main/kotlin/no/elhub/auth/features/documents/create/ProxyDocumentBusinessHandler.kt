package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.changeofsupplier.toChangeOfSupplierBusinessModel
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.command.DocumentCommand
import no.elhub.auth.features.documents.create.command.DocumentMetaMarker
import no.elhub.auth.features.documents.create.command.toDocumentCommand
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.grants.GRANTS_PATH
import no.elhub.auth.features.requests.create.GrantProperties
import no.elhub.auth.features.requests.create.model.defaultRequestValidTo

class ProxyDocumentBusinessHandler(
    private val changeOfSupplierHandler: ChangeOfSupplierBusinessHandler,
) : DocumentBusinessHandler {
    override fun validateAndReturnDocumentCommand(model: CreateDocumentModel): Either<CreateDocumentError, DocumentCommand> {
        return when (model.documentType) {
            AuthorizationDocument.Type.ChangeOfSupplierConfirmation -> changeOfSupplierHandler.validateAndReturnDocumentCommand(model)
        }
    }

    override fun generateFile(
        signatoryId: String,
        documentMeta: DocumentMetaMarker
    ): Either<DocumentGenerationError.ContentGenerationError, ByteArray> {
        return changeOfSupplierHandler.generateFile(signatoryId, documentMeta)
    }

    override fun getGrantProperties(document: AuthorizationDocument): GrantProperties {
        return GrantProperties(
            validFrom = defaultRequestValidTo(),
            validTo = defaultRequestValidTo()
        )
    }

}

interface DocumentBusinessHandler {
    fun validateAndReturnDocumentCommand(model: CreateDocumentModel): Either<CreateDocumentError, DocumentCommand>
    fun generateFile(signatoryId: String, documentMeta: DocumentMetaMarker): Either<DocumentGenerationError.ContentGenerationError, ByteArray>
    fun getGrantProperties(document: AuthorizationDocument): GrantProperties
}

