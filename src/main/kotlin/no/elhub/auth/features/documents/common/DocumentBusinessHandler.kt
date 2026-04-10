package no.elhub.auth.features.documents.common

import arrow.core.Either
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.ChangeOfBalanceSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier.MoveInAndChangeOfBalanceSupplierBusinessHandler
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.command.DocumentCommand
import no.elhub.auth.features.documents.create.dto.SupportedLanguageDTO
import no.elhub.auth.features.grants.common.CreateGrantProperties

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

data class CreateDocumentBusinessModel(
    val authorizedParty: AuthorizationParty,
    val documentType: AuthorizationDocument.Type,
    val requestedBy: AuthorizationParty,
    val requestedFrom: AuthorizationParty,
    val requestedTo: AuthorizationParty,
    val meta: CreateDocumentBusinessMeta,
)

data class CreateDocumentBusinessMeta(
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
    val moveInDate: kotlinx.datetime.LocalDate? = null,
    val language: SupportedLanguageDTO = SupportedLanguageDTO.DEFAULT,
)
