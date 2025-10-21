package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.zipOrAccumulate
import no.elhub.auth.features.documents.AuthorizationDocument

// TODO: Use appropriate regex
private const val REGEX_NUMBERS_LETTERS_SYMBOLS = "^[a-zA-Z0-9_.-]*$"
private const val REGEX_REQUESTED_FROM = REGEX_NUMBERS_LETTERS_SYMBOLS
private const val REGEX_REQUESTED_BY = REGEX_NUMBERS_LETTERS_SYMBOLS
private const val REGEX_METERING_POINT = REGEX_NUMBERS_LETTERS_SYMBOLS

class Command private constructor(
    val type: AuthorizationDocument.Type,
    val requestedFrom: String,
    val requestedFromName: String,
    val requestedBy: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
    val meteringPointId: String,
    val meteringPointAddress: String,
) {
    companion object {
        operator fun invoke(
            type: AuthorizationDocument.Type,
            requestedFrom: String,
            requestedFromName: String,
            requestedBy: String,
            balanceSupplierName: String,
            balanceSupplierContractName: String,
            meteringPointId: String,
            meteringPointAddress: String,
        ): Either<NonEmptyList<ValidationError>, Command> = either {
            // https://arrow-kt.io/learn/typed-errors/validation/#fail-first-vs-accumulation
            zipOrAccumulate(
                { ensure(requestedFrom.isNotBlank()) { ValidationError.MissingRequestedFrom } },
                { ensure(requestedFrom.matches(Regex(REGEX_REQUESTED_FROM))) { ValidationError.InvalidRequestedFrom } },
                { ensure(requestedFromName.isNotBlank()) { ValidationError.MissingRequestedFromName } },
                { ensure(requestedBy.isNotBlank()) { ValidationError.MissingRequestedBy } },
                { ensure(requestedBy.matches(Regex(REGEX_REQUESTED_BY))) { ValidationError.InvalidRequestedBy } },
                { ensure(balanceSupplierName.isNotBlank()) { ValidationError.MissingBalanceSupplierName } },
                { ensure(balanceSupplierContractName.isNotBlank()) { ValidationError.MissingBalanceSupplierContractName } },
                { ensure(meteringPointId.isNotBlank()) { ValidationError.MissingMeteringPointId } },
                { ensure(meteringPointId.matches(Regex(REGEX_METERING_POINT))) { ValidationError.InvalidMeteringPointId } },
                { ensure(meteringPointAddress.isNotBlank()) { ValidationError.MissingMeteringPointAddress } },
            ) { _, _, _, _, _, _, _, _, _, _ ->
                Command(
                    type,
                    requestedFrom,
                    requestedFromName,
                    requestedBy,
                    balanceSupplierName,
                    balanceSupplierContractName,
                    meteringPointId,
                    meteringPointAddress,
                )
            }
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
