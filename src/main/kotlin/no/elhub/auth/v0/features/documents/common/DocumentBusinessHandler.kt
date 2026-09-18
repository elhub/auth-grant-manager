package no.elhub.auth.v0.features.documents.common

import arrow.core.Either
import no.elhub.auth.v0.features.businessprocesses.BusinessProcessError
import no.elhub.auth.v0.features.businessprocesses.changeofbalancesupplier.ChangeOfBalanceSupplierBusinessHandler
import no.elhub.auth.v0.features.businessprocesses.moveinandchangeofbalancesupplier.MoveInAndChangeOfBalanceSupplierBusinessHandler
import no.elhub.auth.v0.features.documents.AuthorizationDocument
import no.elhub.auth.v0.features.documents.create.command.DocumentCommand
import no.elhub.auth.v0.features.grants.common.CreateGrantProperties

class ProxyDocumentBusinessHandler(
    private val changeOfBalanceSupplierHandler: ChangeOfBalanceSupplierBusinessHandler,
    private val moveInAndChangeOfBalanceSupplierHandler: MoveInAndChangeOfBalanceSupplierBusinessHandler,
) : DocumentBusinessHandler {
    override suspend fun validateAndReturnDocumentCommand(model: CreateDocumentBusinessModel): Either<BusinessProcessError, DocumentCommand> =
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
    suspend fun validateAndReturnDocumentCommand(model: CreateDocumentBusinessModel): Either<BusinessProcessError, DocumentCommand>

    fun getCreateGrantProperties(document: AuthorizationDocument): CreateGrantProperties
}
