package no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier.domain

import kotlinx.datetime.LocalDate
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.documents.common.CreateDocumentBusinessModel
import no.elhub.auth.features.documents.create.dto.toSupportedLanguage
import no.elhub.auth.features.filegenerator.SupportedLanguage
import no.elhub.auth.features.requests.common.CreateRequestBusinessModel

data class MoveInAndChangeOfBalanceSupplierBusinessModel(
    val language: SupportedLanguage = SupportedLanguage.DEFAULT,
    val requestedBy: AuthorizationParty,
    val requestedFrom: AuthorizationParty,
    val requestedTo: AuthorizationParty,
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
    val moveInDate: LocalDate?,
    val redirectURI: String? = null,
)

fun CreateRequestBusinessModel.toMoveInAndChangeOfBalanceSupplierBusinessModel(): MoveInAndChangeOfBalanceSupplierBusinessModel =
    MoveInAndChangeOfBalanceSupplierBusinessModel(
        requestedBy = this.requestedBy,
        requestedFrom = this.requestedFrom,
        requestedTo = this.requestedTo,
        requestedFromName = this.meta.requestedFromName,
        requestedForMeteringPointId = this.meta.requestedForMeteringPointId,
        requestedForMeteringPointAddress = this.meta.requestedForMeteringPointAddress,
        balanceSupplierName = this.meta.balanceSupplierName,
        balanceSupplierContractName = this.meta.balanceSupplierContractName,
        moveInDate = this.meta.moveInDate,
        redirectURI = this.meta.redirectURI,
    )

fun CreateDocumentBusinessModel.toMoveInAndChangeOfBalanceSupplierBusinessModel(): MoveInAndChangeOfBalanceSupplierBusinessModel =
    MoveInAndChangeOfBalanceSupplierBusinessModel(
        language = this.meta.language.toSupportedLanguage(),
        requestedBy = this.requestedBy,
        requestedFrom = this.requestedFrom,
        requestedTo = this.requestedTo,
        requestedFromName = this.meta.requestedFromName,
        requestedForMeteringPointId = this.meta.requestedForMeteringPointId,
        requestedForMeteringPointAddress = this.meta.requestedForMeteringPointAddress,
        balanceSupplierName = this.meta.balanceSupplierName,
        balanceSupplierContractName = this.meta.balanceSupplierContractName,
        moveInDate = this.meta.moveInDate,
    )
