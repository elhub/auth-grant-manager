package no.elhub.auth.features.documents.common

import arrow.core.Either
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.businessprocesses.changeofenergysupplier.ChangeOfBalanceSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.moveinandchangeofenergysupplier.MoveInAndChangeOfBalanceSupplierBusinessHandler
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.command.DocumentCommand
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.grants.common.CreateGrantProperties

class ProxyDocumentBusinessHandler(
    private val changeOfBalanceSupplierHandler: ChangeOfBalanceSupplierBusinessHandler,
    private val moveInAndChangeOfBalanceSupplierHandler: MoveInAndChangeOfBalanceSupplierBusinessHandler,
) : DocumentBusinessHandler {
    override suspend fun validateAndReturnDocumentCommand(model: CreateDocumentModel): Either<BusinessProcessError, DocumentCommand> =
        when (model.documentType) {
            AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson -> changeOfBalanceSupplierHandler.validateAndReturnDocumentCommand(model)

            AuthorizationDocument.Type.MoveInAndChangeOfBalanceSupplierForPerson -> moveInAndChangeOfBalanceSupplierHandler.validateAndReturnDocumentCommand(
                model
            )
        }

    override fun getCreateGrantProperties(document: AuthorizationDocument): CreateGrantProperties =
        when (document.type) {
            AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson -> changeOfBalanceSupplierHandler.getCreateGrantProperties(document)
            AuthorizationDocument.Type.MoveInAndChangeOfBalanceSupplierForPerson -> moveInAndChangeOfBalanceSupplierHandler.getCreateGrantProperties(document)
        }
}

interface DocumentBusinessHandler {
    suspend fun validateAndReturnDocumentCommand(model: CreateDocumentModel): Either<BusinessProcessError, DocumentCommand>

    fun getCreateGrantProperties(document: AuthorizationDocument): CreateGrantProperties
}
