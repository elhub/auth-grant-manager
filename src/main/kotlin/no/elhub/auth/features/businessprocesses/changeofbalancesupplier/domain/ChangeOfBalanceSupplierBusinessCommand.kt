package no.elhub.auth.features.businessprocesses.changeofbalancesupplier.domain

import kotlinx.datetime.LocalDate
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.command.DocumentCommand
import no.elhub.auth.features.documents.create.command.DocumentMetaMarker
import no.elhub.auth.features.filegenerator.SupportedLanguage
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.command.RequestMetaMarker
import no.elhub.auth.features.requests.create.command.withTextVersion

private const val CHANGE_OF_BALANCE_SUPPLIER_TEXT_VERSION = "v1"

data class ChangeOfBalanceSupplierBusinessCommand(
    val requestedFrom: PartyIdentifier,
    val requestedBy: PartyIdentifier,
    val requestedTo: PartyIdentifier,
    val validTo: LocalDate,
    val scopes: List<CreateScopeData>,
    val meta: ChangeOfBalanceSupplierBusinessMeta,
)

data class ChangeOfBalanceSupplierBusinessMeta(
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeterNumber: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
    val language: SupportedLanguage? = null,
    val redirectURI: String? = null,
) : RequestMetaMarker,
    DocumentMetaMarker {
    private fun commonMetaAttributes(): Map<String, String> =
        buildMap {
            put("requestedFromName", requestedFromName)
            put("requestedForMeteringPointId", requestedForMeteringPointId)
            put("requestedForMeterNumber", requestedForMeterNumber)
            put("requestedForMeteringPointAddress", requestedForMeteringPointAddress)
            put("balanceSupplierContractName", balanceSupplierContractName)
            put("balanceSupplierName", balanceSupplierName)
            language?.let { put("language", it.code) }
            redirectURI?.let { put("redirectURI", it) }
        }

    override fun toRequestMetaAttributes(): Map<String, String> =
        commonMetaAttributes().withTextVersion(CHANGE_OF_BALANCE_SUPPLIER_TEXT_VERSION)

    override fun toMetaAttributes(): Map<String, String> = commonMetaAttributes()
}

fun ChangeOfBalanceSupplierBusinessCommand.toRequestCommand(): RequestCommand =
    RequestCommand(
        type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
        requestedBy = this.requestedBy,
        requestedFrom = this.requestedFrom,
        requestedTo = this.requestedTo,
        scopes = this.scopes,
        validTo = this.validTo.toTimeZoneOffsetDateTimeAtStartOfDay(),
        meta = this.meta,
    )

fun ChangeOfBalanceSupplierBusinessCommand.toDocumentCommand(): DocumentCommand =
    DocumentCommand(
        type = AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson,
        requestedFrom = this.requestedFrom,
        requestedTo = this.requestedTo,
        requestedBy = this.requestedBy,
        scopes = this.scopes,
        validTo = this.validTo.toTimeZoneOffsetDateTimeAtStartOfDay(),
        meta = this.meta,
    )
