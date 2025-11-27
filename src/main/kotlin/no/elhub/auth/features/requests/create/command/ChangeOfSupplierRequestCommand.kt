package no.elhub.auth.features.requests.create.command

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.common.PartyIdentifier

private const val REGEX_NUMBERS_LETTERS_SYMBOLS = "^[a-zA-Z0-9_.-]*$"
private const val REGEX_REQUESTED_FROM = REGEX_NUMBERS_LETTERS_SYMBOLS
private const val REGEX_REQUESTED_BY = REGEX_NUMBERS_LETTERS_SYMBOLS
private const val REGEX_METERING_POINT = REGEX_NUMBERS_LETTERS_SYMBOLS

data class ChangeOfSupplierRequestMeta(
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String
) : RequestMetaMarker {
    override fun toMetaAttributes(): Map<String, String> =
        mapOf(
            "requestedFromName" to requestedFromName,
            "requestedForMeteringPointId" to requestedForMeteringPointId,
            "requestedForMeteringPointAddress" to requestedForMeteringPointAddress,
            "balanceSupplierName" to balanceSupplierName,
            "balanceSupplierContractName" to balanceSupplierContractName
        )
}

class ChangeOfSupplierRequestCommand private constructor(
    requestedFrom: PartyIdentifier,
    requestedBy: PartyIdentifier,
    requestedTo: PartyIdentifier,
    validTo: String,
    meta: ChangeOfSupplierRequestMeta
) : RequestCommand(
    requestedFrom,
    requestedBy,
    requestedTo,
    validTo,
    meta
) {
    companion object {
        operator fun invoke(
            requestedFrom: PartyIdentifier,
            requestedBy: PartyIdentifier,
            requestedFromName: String,
            requestedTo: PartyIdentifier,
            validTo: String,
            requestedForMeteringPointId: String,
            requestedForMeteringPointAddress: String,
            balanceSupplierName: String,
            balanceSupplierContractName: String,
        ): Either<RequestValidationError, ChangeOfSupplierRequestCommand> {
            // TODO too many if-statements ...

            if (requestedFrom.idValue.isBlank()) {
                return RequestValidationError.MissingRequestedFrom.left()
            }

            if (!requestedFrom.idValue.matches(Regex(REGEX_REQUESTED_FROM))) {
                return RequestValidationError.MissingRequestedFrom.left()
            }

            if (requestedBy.idValue.isBlank()) {
                return RequestValidationError.MissingRequestedBy.left()
            }

            if (!requestedBy.idValue.matches(Regex(REGEX_REQUESTED_BY))) {
                return RequestValidationError.MissingRequestedBy.left()
            }

            if (requestedFromName.isBlank()) {
                return RequestValidationError.MissingRequestedFromName.left()
            }

            if (requestedTo.idValue.isBlank()) {
                return RequestValidationError.MissingRequestedFrom.left()
            }

            if (!requestedTo.idValue.matches(Regex(REGEX_REQUESTED_FROM))) {
                return RequestValidationError.MissingRequestedFrom.left()
            }

            // TODO check that validTo is Date?
            if (validTo.isBlank()) {
                return RequestValidationError.MissingRequestedFrom.left()
            }

            if (requestedForMeteringPointId.isBlank()) {
                return RequestValidationError.MissingMeteringPointId.left()
            }

            if (!requestedForMeteringPointId.matches(Regex(REGEX_METERING_POINT))) {
                return RequestValidationError.MissingMeteringPointId.left()
            }

            if (requestedForMeteringPointAddress.isBlank()) {
                return RequestValidationError.MissingMeteringPointAddress.left()
            }

            if (balanceSupplierName.isBlank()) {
                return RequestValidationError.MissingBalanceSupplierName.left()
            }

            if (balanceSupplierContractName.isBlank()) {
                return RequestValidationError.MissingBalanceSupplierContractName.left()
            }

            val changeOfSupplierMeta = ChangeOfSupplierRequestMeta(
                requestedFromName = requestedFromName,
                requestedForMeteringPointId = requestedForMeteringPointAddress,
                requestedForMeteringPointAddress = requestedForMeteringPointAddress,
                balanceSupplierName = balanceSupplierName,
                balanceSupplierContractName = balanceSupplierContractName
            )

            return ChangeOfSupplierRequestCommand(
                requestedBy = requestedBy,
                requestedFrom = requestedFrom,
                requestedTo = requestedTo,
                validTo = validTo,
                meta = changeOfSupplierMeta
            ).right()
        }
    }
}

sealed class RequestValidationError {
    data object MissingRequestedFrom : RequestValidationError()
    data object MissingRequestedFromName : RequestValidationError()
    data object MissingRequestedBy : RequestValidationError()
    data object MissingBalanceSupplierContractName : RequestValidationError()
    data object MissingBalanceSupplierName : RequestValidationError()
    data object MissingMeteringPointId : RequestValidationError()
    data object MissingMeteringPointAddress : RequestValidationError()
}
