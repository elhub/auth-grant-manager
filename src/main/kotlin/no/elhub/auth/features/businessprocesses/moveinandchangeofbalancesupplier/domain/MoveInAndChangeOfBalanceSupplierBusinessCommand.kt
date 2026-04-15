package no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier.domain

import kotlinx.datetime.LocalDate
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.command.DocumentCommand
import no.elhub.auth.features.documents.create.command.DocumentMetaMarker
import no.elhub.auth.features.filegenerator.SupportedLanguage
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.command.RequestMetaMarker
import no.elhub.auth.features.requests.create.command.withTextVersion

private const val MOVE_IN_AND_CHANGE_OF_BALANCE_SUPPLIER_TEXT_VERSION = "v1"

data class MoveInAndChangeOfBalanceSupplierBusinessCommand(
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
    val moveInDate: LocalDate?,
    val language: SupportedLanguage? = null,
    val redirectURI: String? = null,
) : RequestMetaMarker,
    DocumentMetaMarker {
    fun commonMetaAttributes(): Map<String, String> =
        buildMap {
            put("requestedFromName", requestedFromName)
            put("requestedForMeteringPointId", requestedForMeteringPointId)
            put("requestedForMeterNumber", requestedForMeterNumber)
            put("requestedForMeteringPointAddress", requestedForMeteringPointAddress)
            put("balanceSupplierContractName", balanceSupplierContractName)
            put("balanceSupplierName", balanceSupplierName)
            language?.let { put("language", it.code) }
            moveInDate?.let { put("moveInDate", it.toString()) }
            redirectURI?.let { put("redirectURI", it) }
        }

    override fun toRequestMetaAttributes(): Map<String, String> =
        commonMetaAttributes().withTextVersion(MOVE_IN_AND_CHANGE_OF_BALANCE_SUPPLIER_TEXT_VERSION)

    override fun toMetaAttributes(): Map<String, String> = commonMetaAttributes()
}

fun MoveInAndChangeOfBalanceSupplierBusinessCommand.toRequestCommand(): RequestCommand =
    RequestCommand(
        type = AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson,
        scopes = this.scopes,
        validTo = this.validTo.toTimeZoneOffsetDateTimeAtStartOfDay(),
        meta = this.meta,
    )

fun MoveInAndChangeOfBalanceSupplierBusinessCommand.toDocumentCommand(): DocumentCommand =
    DocumentCommand(
        type = AuthorizationDocument.Type.MoveInAndChangeOfBalanceSupplierForPerson,
        scopes = this.scopes,
        validTo = this.validTo.toTimeZoneOffsetDateTimeAtStartOfDay(),
        meta = this.meta,
    )
