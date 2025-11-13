package no.elhub.auth.features.documents.create.command

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.documents.create.PartyIdentifier

private const val REGEX_NUMBERS_LETTERS_SYMBOLS = "^[a-zA-Z0-9_.-]*$"
private const val REGEX_REQUESTED_FROM = REGEX_NUMBERS_LETTERS_SYMBOLS
private const val REGEX_REQUESTED_BY = REGEX_NUMBERS_LETTERS_SYMBOLS
private const val REGEX_METERING_POINT = REGEX_NUMBERS_LETTERS_SYMBOLS

data class ChangeOfSupplierMetaMarker(
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
    val meteringPointId: String,
    val meteringPointAddress: String,
    val requestedFromName: String,
) : DocumentMetaMarker

class ChangeOfSupplierDocumentCommand private constructor(
    requestedFrom: PartyIdentifier,
    requestedTo: PartyIdentifier,
    requestedBy: PartyIdentifier,
    signedBy: PartyIdentifier,
    meta: ChangeOfSupplierMetaMarker
) : DocumentCommand(
    requestedFrom,
    requestedTo,
    requestedBy,
    signedBy,
    meta
) {
    companion object {
        operator fun invoke(
            requestedBy: PartyIdentifier,
            requestedFrom: PartyIdentifier,
            requestedTo: PartyIdentifier,
            signedBy: PartyIdentifier,
            requestedFromName: String,
            balanceSupplierName: String,
            balanceSupplierContractName: String,
            meteringPointId: String,
            meteringPointAddress: String,
        ): Either<ValidationError, ChangeOfSupplierDocumentCommand> {
                if (requestedFromName.isBlank()) {
                    return ValidationError.MissingRequestedFromName.left()
                }

                if (balanceSupplierName.isBlank()) {
                    return ValidationError.MissingBalanceSupplierName.left()
                }

                if (balanceSupplierContractName.isBlank()) {
                    return ValidationError.MissingBalanceSupplierContractName.left()
                }

                if (meteringPointId.isBlank()) {
                    return ValidationError.MissingMeteringPointId.left()
                }

                if (!meteringPointId.matches(Regex(REGEX_METERING_POINT))) {
                    return ValidationError.MissingMeteringPointId.left()
                }

                if (meteringPointAddress.isBlank()) {
                    return ValidationError.MissingMeteringPointAddress.left()
                }

                if (requestedBy.idValue.isBlank()) {
                    return ValidationError.MissingRequestedBy.left()
                }

                if (!requestedBy.idValue.matches(Regex(REGEX_REQUESTED_BY))) {
                    return ValidationError.MissingRequestedBy.left()
                }

                if (requestedFrom.idValue.isBlank()) {
                    return ValidationError.MissingRequestedFrom.left()
                }

                if (!requestedFrom.idValue.matches(Regex(REGEX_REQUESTED_FROM))) {
                    return ValidationError.MissingRequestedFrom.left()
                }

            val changeOfSupplierMeta = ChangeOfSupplierMetaMarker(
                balanceSupplierName = balanceSupplierName,
                balanceSupplierContractName = balanceSupplierContractName,
                meteringPointId = meteringPointId,
                meteringPointAddress = meteringPointAddress,
                requestedFromName = requestedFromName
            )

            return ChangeOfSupplierDocumentCommand(
                requestedBy = requestedBy,
                requestedFrom = requestedFrom,
                requestedTo = requestedTo,
                signedBy = signedBy,
                meta = changeOfSupplierMeta
            ).right()
        }
    }
}

sealed class ValidationError {
    data object MissingRequestedFrom : ValidationError()
    data object InvalidRequestedFrom : ValidationError()
    data object MissingRequestedFromName : ValidationError()
    data object MissingRequestedBy : ValidationError()
    data object InvalidRequestedBy : ValidationError()
    data object MissingBalanceSupplierName : ValidationError()
    data object MissingBalanceSupplierContractName : ValidationError()
    data object MissingMeteringPointId : ValidationError()
    data object InvalidMeteringPointId : ValidationError()
    data object MissingMeteringPointAddress : ValidationError()
}