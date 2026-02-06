package no.elhub.auth.features.businessprocesses.changeofsupplier.domain

import kotlinx.datetime.LocalDate
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.command.DocumentCommand
import no.elhub.auth.features.documents.create.command.DocumentMetaMarker
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.command.RequestMetaMarker

data class ChangeOfSupplierBusinessCommand(
    val requestedFrom: PartyIdentifier,
    val requestedBy: PartyIdentifier,
    val requestedTo: PartyIdentifier,
    val validTo: LocalDate,
    val scopes: List<CreateScopeData>,
    val meta: ChangeOfSupplierBusinessMeta,
)

data class ChangeOfSupplierBusinessMeta(
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
    val redirectURI: String? = null,
) : RequestMetaMarker,
    DocumentMetaMarker {
    override fun toMetaAttributes(): Map<String, String> =
        buildMap {
            put("requestedFromName", requestedFromName)
            put("requestedForMeteringPointId", requestedForMeteringPointId)
            put("requestedForMeteringPointAddress", requestedForMeteringPointAddress)
            put("balanceSupplierContractName", balanceSupplierContractName)
            put("balanceSupplierName", balanceSupplierName)
            redirectURI?.let { put("redirectURI", it) }
        }
}

fun ChangeOfSupplierBusinessCommand.toRequestCommand(): RequestCommand =
    RequestCommand(
        type = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
        requestedBy = this.requestedBy,
        requestedFrom = this.requestedFrom,
        requestedTo = this.requestedTo,
        scopes = this.scopes,
        validTo = this.validTo.toTimeZoneOffsetDateTimeAtStartOfDay(),
        meta = this.meta,
    )

fun ChangeOfSupplierBusinessCommand.toDocumentCommand(): DocumentCommand =
    DocumentCommand(
        type = AuthorizationDocument.Type.ChangeOfEnergySupplierForPerson,
        requestedFrom = this.requestedFrom,
        requestedTo = this.requestedTo,
        requestedBy = this.requestedBy,
        scopes = this.scopes,
        validTo = this.validTo.toTimeZoneOffsetDateTimeAtStartOfDay(),
        meta = this.meta,
    )
