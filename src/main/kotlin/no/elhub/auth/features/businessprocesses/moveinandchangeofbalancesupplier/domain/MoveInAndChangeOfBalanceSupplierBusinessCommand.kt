package no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier.domain

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

data class MoveInAndChangeOfBalanceSupplierBusinessCommand(
    val requestedFrom: PartyIdentifier,
    val requestedBy: PartyIdentifier,
    val requestedTo: PartyIdentifier,
    val validTo: LocalDate,
    val scopes: List<CreateScopeData>,
    val meta: MoveInAndChangeOfBalanceSupplierBusinessMeta,
)

data class MoveInAndChangeOfBalanceSupplierBusinessMeta(
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeterNumber: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
    val startDate: LocalDate?,
    val language: SupportedLanguage? = null,
    val redirectURI: String? = null,
) : RequestMetaMarker,
    DocumentMetaMarker {
    override fun toMetaAttributes(): Map<String, String> =
        buildMap {
            put("requestedFromName", requestedFromName)
            put("requestedForMeteringPointId", requestedForMeteringPointId)
            put("requestedForMeterNumber", requestedForMeterNumber)
            put("requestedForMeteringPointAddress", requestedForMeteringPointAddress)
            put("balanceSupplierContractName", balanceSupplierContractName)
            put("balanceSupplierName", balanceSupplierName)
            language?.let { put("language", it.code) }
            startDate?.let { put("startDate", it.toString()) }
            redirectURI?.let { put("redirectURI", it) }
        }
}

fun MoveInAndChangeOfBalanceSupplierBusinessCommand.toRequestCommand(): RequestCommand =
    RequestCommand(
        type = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
        requestedBy = this.requestedBy,
        requestedFrom = this.requestedFrom,
        requestedTo = this.requestedTo,
        scopes = this.scopes,
        validTo = this.validTo.toTimeZoneOffsetDateTimeAtStartOfDay(),
        meta = this.meta,
    )

fun MoveInAndChangeOfBalanceSupplierBusinessCommand.toDocumentCommand(): DocumentCommand =
    DocumentCommand(
        type = AuthorizationDocument.Type.MoveInAndChangeOfBalanceSupplierForPerson,
        requestedFrom = this.requestedFrom,
        requestedTo = this.requestedTo,
        requestedBy = this.requestedBy,
        scopes = this.scopes,
        validTo = this.validTo.toTimeZoneOffsetDateTimeAtStartOfDay(),
        meta = this.meta,
    )
