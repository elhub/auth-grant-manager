package no.elhub.auth.features.requests.create.requesttypes.changeofsupplierconfirmation

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.command.RequestMetaMarker
import no.elhub.auth.features.requests.create.model.CreateRequestModel
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeHandler
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeValidationError
import no.elhub.auth.features.requests.create.requesttypes.changeofsupplierconfirmation.ChangeOfSupplierConfirmationValidationError

private const val REGEX_NUMBERS_LETTERS_SYMBOLS = "^[a-zA-Z0-9_.-]*$"
private const val REGEX_REQUESTED_FROM = REGEX_NUMBERS_LETTERS_SYMBOLS
private const val REGEX_REQUESTED_BY = REGEX_NUMBERS_LETTERS_SYMBOLS
private const val REGEX_METERING_POINT = "^\\d{18}$"

class ChangeOfSupplierConfirmationRequestTypeHandler : RequestTypeHandler {
    override suspend fun handle(model: CreateRequestModel): Either<RequestTypeValidationError, RequestCommand> {
        val meta = model.meta

        if (meta.requestedFromName.isBlank()) {
            return ChangeOfSupplierConfirmationValidationError.MissingRequestedFromName.left()
        }

        if (meta.balanceSupplierContractName.isBlank()) {
            return ChangeOfSupplierConfirmationValidationError.MissingBalanceSupplierConfirmationContractName.left()
        }

        if (meta.requestedForMeteringPointId.isBlank()) {
            return ChangeOfSupplierConfirmationValidationError.MissingMeteringPointId.left()
        }

        if (!meta.requestedForMeteringPointId.matches(Regex(REGEX_METERING_POINT))) {
            return ChangeOfSupplierConfirmationValidationError.InvalidMeteringPointId.left()
        }

        if (meta.requestedForMeteringPointAddress.isBlank()) {
            return ChangeOfSupplierConfirmationValidationError.MissingMeteringPointAddress.left()
        }

        if (meta.requestedBy.idValue.isBlank()) {
            return ChangeOfSupplierConfirmationValidationError.MissingRequestedBy.left()
        }

        if (!meta.requestedBy.idValue.matches(Regex(REGEX_REQUESTED_BY))) {
            return ChangeOfSupplierConfirmationValidationError.InvalidRequestedBy.left()
        }

        if (meta.requestedFrom.idValue.isBlank()) {
            return ChangeOfSupplierConfirmationValidationError.MissingRequestedFrom.left()
        }

        if (!meta.requestedFrom.idValue.matches(Regex(REGEX_REQUESTED_FROM))) {
            return ChangeOfSupplierConfirmationValidationError.InvalidRequestedFrom.left()
        }

        val changeOfSupplierMeta =
            ChangeOfSupplierRequestMeta(
                requestedFromName = meta.requestedFromName,
                requestedForMeteringPointId = meta.requestedForMeteringPointId,
                requestedForMeteringPointAddress = meta.requestedForMeteringPointAddress,
                balanceSupplierContractName = meta.balanceSupplierContractName,
                balanceSupplierName = meta.balanceSupplierName,
            )

        val command =
            RequestCommand(
                type = AuthorizationRequest.Type.ChangeOfSupplierConfirmation,
                requestedBy = meta.requestedBy,
                requestedFrom = meta.requestedFrom,
                requestedTo = meta.requestedTo,
                validTo = model.validTo,
                meta = changeOfSupplierMeta,
            )

        return command.right()
    }
}

private data class ChangeOfSupplierRequestMeta(
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
) : RequestMetaMarker {
    override fun toMetaAttributes(): Map<String, String> =
        mapOf(
            "requestedFromName" to requestedFromName,
            "requestedForMeteringPointId" to requestedForMeteringPointId,
            "requestedForMeteringPointAddress" to requestedForMeteringPointAddress,
            "balanceSupplierContractName" to balanceSupplierContractName,
            "balanceSupplierName" to balanceSupplierName,
        )
}
