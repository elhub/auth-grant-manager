package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.documents.AuthorizationDocument

// TODO: Use appropriate regex
private const val REGEX_NUMBERS_LETTERS_SYMBOLS = "^[a-zA-Z0-9_.-]*$"
private const val REGEX_REQUESTED_FROM = REGEX_NUMBERS_LETTERS_SYMBOLS
private const val REGEX_REQUESTED_BY = REGEX_NUMBERS_LETTERS_SYMBOLS
private const val REGEX_METERING_POINT = REGEX_NUMBERS_LETTERS_SYMBOLS

class Command private constructor(
    val type: AuthorizationDocument.Type,
    val requestedByIdentifier: PartyIdentifier,
    val requestedFromIdentifier: PartyIdentifier,
    val requestedToIdentifier: PartyIdentifier,
    val signedByIdentifier: PartyIdentifier,
    val requestedFromName: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
    val meteringPointId: String,
    val meteringPointAddress: String,
) {
    companion object {
        operator fun invoke(
            type: AuthorizationDocument.Type,
            requestedByIdentifier: PartyIdentifier,
            requestedFromIdentifier: PartyIdentifier,
            requestedToIdentifier: PartyIdentifier,
            signedByIdentifier: PartyIdentifier,
            requestedFromName: String,
            balanceSupplierName: String,
            balanceSupplierContractName: String,
            meteringPointId: String,
            meteringPointAddress: String,
        ): Either<ValidationError, Command> {
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

            if (requestedByIdentifier.idValue.isBlank()) {
                return ValidationError.MissingRequestedBy.left()
            }

            if (!requestedByIdentifier.idValue.matches(Regex(REGEX_REQUESTED_BY))) {
                return ValidationError.MissingRequestedBy.left()
            }

            if (requestedFromIdentifier.idValue.isBlank()) {
                return ValidationError.MissingRequestedFrom.left()
            }

            if (!requestedFromIdentifier.idValue.matches(Regex(REGEX_REQUESTED_FROM))) {
                return ValidationError.MissingRequestedFrom.left()
            }

            return Command(
                type = type,
                requestedByIdentifier = requestedByIdentifier,
                requestedFromIdentifier = requestedFromIdentifier,
                requestedToIdentifier = requestedToIdentifier,
                signedByIdentifier = signedByIdentifier,
                requestedFromName = requestedFromName,
                balanceSupplierName = balanceSupplierName,
                balanceSupplierContractName = balanceSupplierContractName,
                meteringPointId = meteringPointId,
                meteringPointAddress = meteringPointAddress,
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
