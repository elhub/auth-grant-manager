package no.elhub.auth.features.documents.common

import arrow.core.Either
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.movein.MoveInBusinessHandler
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.CreateError
import no.elhub.auth.features.documents.create.command.DocumentCommand
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.grants.common.CreateGrantProperties

class ProxyDocumentBusinessHandler(
    private val changeOfSupplierHandler: ChangeOfSupplierBusinessHandler,
    private val moveInHandler: MoveInBusinessHandler,
) : DocumentBusinessHandler {
    override suspend fun validateAndReturnDocumentCommand(model: CreateDocumentModel): Either<CreateError.BusinessValidationError, DocumentCommand> =
        when (model.documentType) {
            AuthorizationDocument.Type.ChangeOfEnergySupplierForPerson -> changeOfSupplierHandler.validateAndReturnDocumentCommand(model)
            AuthorizationDocument.Type.MoveInAndChangeOfEnergySupplierForPerson -> moveInHandler.validateAndReturnDocumentCommand(model)
        }

    override fun getCreateGrantProperties(document: AuthorizationDocument): CreateGrantProperties =
        when (document.type) {
            AuthorizationDocument.Type.ChangeOfEnergySupplierForPerson -> changeOfSupplierHandler.getCreateGrantProperties(document)
            AuthorizationDocument.Type.MoveInAndChangeOfEnergySupplierForPerson -> moveInHandler.getCreateGrantProperties(document)
        }
}

interface DocumentBusinessHandler {
    suspend fun validateAndReturnDocumentCommand(model: CreateDocumentModel): Either<CreateError.BusinessValidationError, DocumentCommand>

    fun getCreateGrantProperties(document: AuthorizationDocument): CreateGrantProperties
}
