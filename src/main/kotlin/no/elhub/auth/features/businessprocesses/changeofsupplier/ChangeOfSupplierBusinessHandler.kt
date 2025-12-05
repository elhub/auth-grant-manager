package no.elhub.auth.features.businessprocesses.changeofsupplier

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.common.PartyIdentifier
import no.elhub.auth.features.documents.create.command.DocumentMetaMarker
import no.elhub.auth.features.requests.create.command.RequestMetaMarker

private const val REGEX_NUMBERS_LETTERS_SYMBOLS = "^[a-zA-Z0-9_.-]*$"
private const val REGEX_REQUESTED_FROM = REGEX_NUMBERS_LETTERS_SYMBOLS
private const val REGEX_REQUESTED_BY = REGEX_NUMBERS_LETTERS_SYMBOLS
private const val REGEX_METERING_POINT = REGEX_NUMBERS_LETTERS_SYMBOLS

data class ChangeOfSupplierBusinessModel(
    val requestedBy: PartyIdentifier,
    val requestedFrom: PartyIdentifier,
    val requestedTo: PartyIdentifier,
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
)

data class ChangeOfSupplierBusinessCommand(
    val requestedFrom: PartyIdentifier,
    val requestedBy: PartyIdentifier,
    val requestedTo: PartyIdentifier,
    val meta: ChangeOfSupplierBusinessMeta,
)

data class ChangeOfSupplierBusinessMeta(
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
) : RequestMetaMarker,
    DocumentMetaMarker {
    override fun toMetaAttributes(): Map<String, String> =
        mapOf(
            "requestedFromName" to requestedFromName,
            "requestedForMeteringPointId" to requestedForMeteringPointId,
            "requestedForMeteringPointAddress" to requestedForMeteringPointAddress,
            "balanceSupplierContractName" to balanceSupplierContractName,
            "balanceSupplierName" to balanceSupplierName,
        )
}

class ChangeOfSupplierBusinessHandler {
    fun handle(model: ChangeOfSupplierBusinessModel): Either<ChangeOfSupplierValidationError, ChangeOfSupplierBusinessCommand> {
        if (model.requestedFromName.isBlank()) {
            return ChangeOfSupplierValidationError.MissingRequestedFromName.left()
        }

        if (model.balanceSupplierName.isBlank()) {
            return ChangeOfSupplierValidationError.MissingBalanceSupplierName.left()
        }

        if (model.balanceSupplierContractName.isBlank()) {
            return ChangeOfSupplierValidationError.MissingBalanceSupplierContractName.left()
        }

        if (model.requestedForMeteringPointId.isBlank()) {
            return ChangeOfSupplierValidationError.MissingMeteringPointId.left()
        }

        if (!model.requestedForMeteringPointId.matches(Regex(REGEX_METERING_POINT))) {
            return ChangeOfSupplierValidationError.InvalidMeteringPointId.left()
        }

        if (model.requestedForMeteringPointAddress.isBlank()) {
            return ChangeOfSupplierValidationError.MissingMeteringPointAddress.left()
        }

        if (model.requestedBy.idValue.isBlank()) {
            return ChangeOfSupplierValidationError.MissingRequestedBy.left()
        }

        if (!model.requestedBy.idValue.matches(Regex(REGEX_REQUESTED_BY))) {
            return ChangeOfSupplierValidationError.InvalidRequestedBy.left()
        }

        if (model.requestedFrom.idValue.isBlank()) {
            return ChangeOfSupplierValidationError.MissingRequestedFrom.left()
        }

        if (!model.requestedFrom.idValue.matches(Regex(REGEX_REQUESTED_FROM))) {
            return ChangeOfSupplierValidationError.InvalidRequestedFrom.left()
        }

        val meta =
            ChangeOfSupplierBusinessMeta(
                requestedFromName = model.requestedFromName,
                requestedForMeteringPointId = model.requestedForMeteringPointId,
                requestedForMeteringPointAddress = model.requestedForMeteringPointAddress,
                balanceSupplierContractName = model.balanceSupplierContractName,
                balanceSupplierName = model.balanceSupplierName,
            )

        return ChangeOfSupplierBusinessCommand(
            requestedFrom = model.requestedFrom,
            requestedBy = model.requestedBy,
            requestedTo = model.requestedTo,
            meta = meta,
        ).right()
    }
}
