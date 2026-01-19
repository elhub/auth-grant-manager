package no.elhub.auth.features.documents.common

import arrow.core.Either
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.movein.MoveInBusinessHandler
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.CreateDocumentError
import no.elhub.auth.features.documents.create.CreateDocumentError.BusinessValidationError
import no.elhub.auth.features.documents.create.DocumentGenerationError
import no.elhub.auth.features.documents.create.FileGenerator
import no.elhub.auth.features.documents.create.command.DocumentCommand
import no.elhub.auth.features.documents.create.command.DocumentMetaMarker
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.grants.common.CreateGrantProperties

class ProxyDocumentBusinessHandler(
    private val changeOfSupplierHandler: ChangeOfSupplierBusinessHandler,
    private val moveInHandler: MoveInBusinessHandler,
    private val fileGenerator: FileGenerator,
) : DocumentBusinessHandler {
    override fun validateAndReturnDocumentCommand(model: CreateDocumentModel): Either<BusinessValidationError, DocumentCommand> =
        when (model.documentType) {
            AuthorizationDocument.Type.ChangeOfSupplierConfirmation -> changeOfSupplierHandler.validateAndReturnDocumentCommand(model)
            AuthorizationDocument.Type.MoveIn -> moveInHandler.validateAndReturnDocumentCommand(model)
        }

    override fun getCreateGrantProperties(document: AuthorizationDocument): CreateGrantProperties =
        when (document.type) {
            AuthorizationDocument.Type.ChangeOfSupplierConfirmation -> changeOfSupplierHandler.getCreateGrantProperties(document)
            AuthorizationDocument.Type.MoveIn -> moveInHandler.getCreateGrantProperties(document)
        }

    fun generateFile(
        signatoryId: String,
        documentMeta: DocumentMetaMarker,
    ): Either<DocumentGenerationError.ContentGenerationError, ByteArray> = fileGenerator.generate(signatoryId, documentMeta)
}

interface DocumentBusinessHandler {
    fun validateAndReturnDocumentCommand(model: CreateDocumentModel): Either<CreateDocumentError, DocumentCommand>

    fun getCreateGrantProperties(document: AuthorizationDocument): CreateGrantProperties
}
