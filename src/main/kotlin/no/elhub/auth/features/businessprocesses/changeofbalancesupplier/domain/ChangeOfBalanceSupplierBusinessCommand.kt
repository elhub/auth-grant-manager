package no.elhub.auth.features.businessprocesses.changeofbalancesupplier.domain

import kotlinx.datetime.LocalDate
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

private const val CHANGE_OF_BALANCE_SUPPLIER_TEXT_VERSION = "v1"

data class ChangeOfBalanceSupplierBusinessCommand(
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
    private fun commonMetaAttributes(): AuthorizationMetadata =
        buildMap {
            put(AuthorizationMetadataKeys.REQUESTED_FROM_NAME, requestedFromName)
            put(AuthorizationMetadataKeys.REQUESTED_FOR_METERING_POINT_ID, requestedForMeteringPointId)
            put(AuthorizationMetadataKeys.REQUESTED_FOR_METER_NUMBER, requestedForMeterNumber)
            put(AuthorizationMetadataKeys.REQUESTED_FOR_METERING_POINT_ADDRESS, requestedForMeteringPointAddress)
            put(AuthorizationMetadataKeys.BALANCE_SUPPLIER_CONTRACT_NAME, balanceSupplierContractName)
            put(AuthorizationMetadataKeys.BALANCE_SUPPLIER_NAME, balanceSupplierName)
            language?.let { put(AuthorizationMetadataKeys.LANGUAGE, it.code) }
            redirectURI?.let { put(AuthorizationMetadataKeys.REDIRECT_URI, it) }
        }.toStringAuthorizationMetadata()

    override fun toRequestMetaAttributes(): AuthorizationMetadata =
        commonMetaAttributes().withTextVersion(CHANGE_OF_BALANCE_SUPPLIER_TEXT_VERSION)

    override fun toMetaAttributes(): AuthorizationMetadata = commonMetaAttributes()
}

fun ChangeOfBalanceSupplierBusinessCommand.toRequestCommand(): RequestCommand =
    RequestCommand(
        type = AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson,
        scopes = this.scopes,
        validTo = this.validTo.toTimeZoneOffsetDateTimeAtStartOfDay(),
        meta = this.meta,
    )

fun ChangeOfBalanceSupplierBusinessCommand.toDocumentCommand(): DocumentCommand =
    DocumentCommand(
        type = AuthorizationDocument.Type.ChangeOfBalanceSupplierForPerson,
        scopes = this.scopes,
        validTo = this.validTo.toTimeZoneOffsetDateTimeAtStartOfDay(),
        meta = this.meta,
    )
