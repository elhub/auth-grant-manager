package no.elhub.auth.features.documents.common

import arrow.core.Either
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierBusinessHandler
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.CreateDocumentError
import no.elhub.auth.features.documents.create.DocumentGenerationError
import no.elhub.auth.features.documents.create.FileGenerator
import no.elhub.auth.features.documents.create.command.DocumentCommand
import no.elhub.auth.features.documents.create.command.DocumentMetaMarker
import no.elhub.auth.features.documents.create.model.CreateDocumentRequestModel
import no.elhub.auth.features.grants.common.CreateGrantProperties

class ProxyDocumentBusinessHandler(
    private val changeOfSupplierHandler: ChangeOfSupplierBusinessHandler,
    private val fileGenerator: FileGenerator,
) : DocumentBusinessHandler {
    override fun validateAndReturnDocumentCommand(model: CreateDocumentRequestModel): Either<CreateDocumentError, DocumentCommand> =
        when (model.documentType) {
            AuthorizationDocument.Type.ChangeOfSupplierConfirmation -> changeOfSupplierHandler.validateAndReturnDocumentCommand(model)
        }

    override fun getCreateGrantProperties(document: AuthorizationDocument): CreateGrantProperties =
        when (document.type) {
            AuthorizationDocument.Type.ChangeOfSupplierConfirmation -> changeOfSupplierHandler.getCreateGrantProperties(document)
        }

    fun generateFile(
        signatoryId: String,
        documentMeta: DocumentMetaMarker,
    ): Either<DocumentGenerationError.ContentGenerationError, ByteArray> = fileGenerator.generate(signatoryId, documentMeta)
}

interface DocumentBusinessHandler {
    fun validateAndReturnDocumentCommand(model: CreateDocumentRequestModel): Either<CreateDocumentError, DocumentCommand>

    fun getCreateGrantProperties(document: AuthorizationDocument): CreateGrantProperties
}
