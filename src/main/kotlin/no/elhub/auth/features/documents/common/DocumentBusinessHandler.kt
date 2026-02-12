package no.elhub.auth.features.documents.common

import arrow.core.Either
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.businessprocesses.changeofenergysupplier.ChangeOfEnergySupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.moveinandchangeofenergysupplier.MoveInAndChangeOfEnergySupplierBusinessHandler
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.command.DocumentCommand
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.grants.common.CreateGrantProperties

class ProxyDocumentBusinessHandler(
    private val changeOfEnergySupplierHandler: ChangeOfEnergySupplierBusinessHandler,
    private val moveInAndChangeOfEnergySupplierHandler: MoveInAndChangeOfEnergySupplierBusinessHandler,
) : DocumentBusinessHandler {
    override suspend fun validateAndReturnDocumentCommand(model: CreateDocumentModel): Either<BusinessProcessError, DocumentCommand> =
        when (model.documentType) {
            AuthorizationDocument.Type.ChangeOfEnergySupplierForPerson -> changeOfEnergySupplierHandler.validateAndReturnDocumentCommand(model)

            AuthorizationDocument.Type.MoveInAndChangeOfEnergySupplierForPerson -> moveInAndChangeOfEnergySupplierHandler.validateAndReturnDocumentCommand(
                model
            )
        }

    override fun getCreateGrantProperties(document: AuthorizationDocument): CreateGrantProperties =
        when (document.type) {
            AuthorizationDocument.Type.ChangeOfEnergySupplierForPerson -> changeOfEnergySupplierHandler.getCreateGrantProperties(document)
            AuthorizationDocument.Type.MoveInAndChangeOfEnergySupplierForPerson -> moveInAndChangeOfEnergySupplierHandler.getCreateGrantProperties(document)
        }
}

interface DocumentBusinessHandler {
    suspend fun validateAndReturnDocumentCommand(model: CreateDocumentModel): Either<BusinessProcessError, DocumentCommand>

    fun getCreateGrantProperties(document: AuthorizationDocument): CreateGrantProperties
}
