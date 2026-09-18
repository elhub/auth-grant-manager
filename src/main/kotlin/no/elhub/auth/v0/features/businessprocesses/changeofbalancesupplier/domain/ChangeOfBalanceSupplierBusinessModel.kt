package no.elhub.auth.v0.features.businessprocesses.changeofbalancesupplier.domain

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.documents.common.CreateDocumentBusinessModel
import no.elhub.auth.v0.features.documents.create.dto.toSupportedLanguage
import no.elhub.auth.v0.features.filegenerator.SupportedLanguage
import no.elhub.auth.v0.features.requests.common.CreateRequestBusinessModel

data class ChangeOfBalanceSupplierBusinessModel(
    val language: SupportedLanguage = SupportedLanguage.DEFAULT,
    val requestedBy: AuthorizationParty,
    val requestedFrom: AuthorizationParty,
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
    val redirectURI: String? = null,
)

fun CreateRequestBusinessModel.toChangeOfBalanceSupplierBusinessModel(): ChangeOfBalanceSupplierBusinessModel =
    ChangeOfBalanceSupplierBusinessModel(
        requestedBy = this.requestedBy,
        requestedFrom = this.requestedFrom,
        requestedFromName = this.meta.requestedFromName,
        requestedForMeteringPointId = this.meta.requestedForMeteringPointId,
        requestedForMeteringPointAddress = this.meta.requestedForMeteringPointAddress,
        balanceSupplierName = this.meta.balanceSupplierName,
        balanceSupplierContractName = this.meta.balanceSupplierContractName,
        redirectURI = this.meta.redirectURI,
    )

fun CreateDocumentBusinessModel.toChangeOfBalanceSupplierBusinessModel(): ChangeOfBalanceSupplierBusinessModel =
    ChangeOfBalanceSupplierBusinessModel(
        language = this.meta.language.toSupportedLanguage(),
        requestedBy = this.requestedBy,
        requestedFrom = this.requestedFrom,
        requestedFromName = this.meta.requestedFromName,
        requestedForMeteringPointId = this.meta.requestedForMeteringPointId,
        requestedForMeteringPointAddress = this.meta.requestedForMeteringPointAddress,
        balanceSupplierName = this.meta.balanceSupplierName,
        balanceSupplierContractName = this.meta.balanceSupplierContractName,
    )
