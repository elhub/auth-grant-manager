package no.elhub.auth.features.businessprocesses.moveinandchangeofenergysupplier.domain

import kotlinx.datetime.LocalDate
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.documents.create.dto.toSupportedLanguage
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.filegenerator.SupportedLanguage
import no.elhub.auth.features.requests.create.model.CreateRequestModel

data class MoveInAndChangeOfBalanceSupplierBusinessModel(
    val language: SupportedLanguage = SupportedLanguage.DEFAULT,
    val requestedBy: PartyIdentifier,
    val requestedFrom: PartyIdentifier,
    val requestedTo: PartyIdentifier,
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
    val startDate: LocalDate?,
    val redirectURI: String? = null,
)

fun CreateRequestModel.toMoveInAndChangeOfBalanceSupplierBusinessModel(): MoveInAndChangeOfBalanceSupplierBusinessModel =
    MoveInAndChangeOfBalanceSupplierBusinessModel(
        requestedBy = this.meta.requestedBy,
        requestedFrom = this.meta.requestedFrom,
        requestedTo = this.meta.requestedTo,
        requestedFromName = this.meta.requestedFromName,
        requestedForMeteringPointId = this.meta.requestedForMeteringPointId,
        requestedForMeteringPointAddress = this.meta.requestedForMeteringPointAddress,
        balanceSupplierName = this.meta.balanceSupplierName,
        balanceSupplierContractName = this.meta.balanceSupplierContractName,
        startDate = this.meta.startDate,
        redirectURI = this.meta.redirectURI,
    )

fun CreateDocumentModel.toMoveInAndChangeOfBalanceSupplierBusinessModel(): MoveInAndChangeOfBalanceSupplierBusinessModel =
    MoveInAndChangeOfBalanceSupplierBusinessModel(
        language = this.meta.language.toSupportedLanguage(),
        requestedBy = this.meta.requestedBy,
        requestedFrom = this.meta.requestedFrom,
        requestedTo = this.meta.requestedTo,
        requestedFromName = this.meta.requestedFromName,
        requestedForMeteringPointId = this.meta.requestedForMeteringPointId,
        requestedForMeteringPointAddress = this.meta.requestedForMeteringPointAddress,
        balanceSupplierName = this.meta.balanceSupplierName,
        balanceSupplierContractName = this.meta.balanceSupplierContractName,
        startDate = this.meta.startDate,
    )
