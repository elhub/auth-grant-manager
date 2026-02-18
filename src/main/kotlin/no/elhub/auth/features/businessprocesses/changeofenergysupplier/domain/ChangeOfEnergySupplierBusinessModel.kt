package no.elhub.auth.features.businessprocesses.changeofenergysupplier.domain

import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.documents.create.dto.toSupportedLanguage
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.filegenerator.SupportedLanguage
import no.elhub.auth.features.requests.create.model.CreateRequestModel

data class ChangeOfEnergySupplierBusinessModel(
    val language: SupportedLanguage = SupportedLanguage.DEFAULT,
    val requestedBy: PartyIdentifier,
    val requestedFrom: PartyIdentifier,
    val requestedTo: PartyIdentifier,
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
    val redirectURI: String? = null,
)

fun CreateRequestModel.toChangeOfEnergySupplierBusinessModel(): ChangeOfEnergySupplierBusinessModel =
    ChangeOfEnergySupplierBusinessModel(
        requestedBy = this.meta.requestedBy,
        requestedFrom = this.meta.requestedFrom,
        requestedTo = this.meta.requestedTo,
        requestedFromName = this.meta.requestedFromName,
        requestedForMeteringPointId = this.meta.requestedForMeteringPointId,
        requestedForMeteringPointAddress = this.meta.requestedForMeteringPointAddress,
        balanceSupplierName = this.meta.balanceSupplierName,
        balanceSupplierContractName = this.meta.balanceSupplierContractName,
        redirectURI = this.meta.redirectURI,
    )

fun CreateDocumentModel.toChangeOfEnergySupplierBusinessModel(): ChangeOfEnergySupplierBusinessModel =
    ChangeOfEnergySupplierBusinessModel(
        language = this.meta.language.toSupportedLanguage(),
        requestedBy = this.meta.requestedBy,
        requestedFrom = this.meta.requestedFrom,
        requestedTo = this.meta.requestedTo,
        requestedFromName = this.meta.requestedFromName,
        requestedForMeteringPointId = this.meta.requestedForMeteringPointId,
        requestedForMeteringPointAddress = this.meta.requestedForMeteringPointAddress,
        balanceSupplierName = this.meta.balanceSupplierName,
        balanceSupplierContractName = this.meta.balanceSupplierContractName,
    )
