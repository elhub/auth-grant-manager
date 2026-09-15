package no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import no.elhub.auth.features.common.AuthorizationMetadata
import no.elhub.auth.features.common.AuthorizationMetadataKeys
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.toStringAuthorizationMetadata
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
    fun commonMetaAttributes(): AuthorizationMetadata =
        buildMap {
            put(AuthorizationMetadataKeys.REQUESTED_FROM_NAME, requestedFromName)
            put(AuthorizationMetadataKeys.REQUESTED_FOR_METERING_POINT_ID, requestedForMeteringPointId)
            put(AuthorizationMetadataKeys.REQUESTED_FOR_METER_NUMBER, requestedForMeterNumber)
            put(AuthorizationMetadataKeys.REQUESTED_FOR_METERING_POINT_ADDRESS, requestedForMeteringPointAddress)
            put(AuthorizationMetadataKeys.BALANCE_SUPPLIER_CONTRACT_NAME, balanceSupplierContractName)
            put(AuthorizationMetadataKeys.BALANCE_SUPPLIER_NAME, balanceSupplierName)
            language?.let { put(AuthorizationMetadataKeys.LANGUAGE, it.code) }
            moveInDate?.let { put(AuthorizationMetadataKeys.MOVE_IN_DATE, it.toString()) }
        }.toStringAuthorizationMetadata()

    override fun toRequestMetaAttributes(): AuthorizationMetadata =
        buildMap {
            putAll(commonMetaAttributes())
            redirectURI?.let { put(AuthorizationMetadataKeys.REDIRECT_URI, JsonPrimitive(it)) }
        }.withTextVersion(MOVE_IN_AND_CHANGE_OF_BALANCE_SUPPLIER_TEXT_VERSION)

    override fun toMetaAttributes(): AuthorizationMetadata = commonMetaAttributes()
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
