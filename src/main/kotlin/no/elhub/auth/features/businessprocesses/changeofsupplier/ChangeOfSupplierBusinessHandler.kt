package no.elhub.auth.features.businessprocesses.changeofsupplier

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import kotlinx.datetime.LocalDate
import no.elhub.auth.features.common.PartyIdentifier
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentBusinessHandler
import no.elhub.auth.features.documents.create.CreateDocumentError
import no.elhub.auth.features.documents.create.command.DocumentCommand
import no.elhub.auth.features.documents.create.command.DocumentMetaMarker
import no.elhub.auth.features.documents.create.command.toDocumentCommand
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.grants.common.CreateGrantProperties
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.RequestBusinessHandler
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.command.RequestMetaMarker
import no.elhub.auth.features.requests.create.model.CreateRequestModel
import no.elhub.auth.features.requests.create.model.defaultRequestValidTo
import no.elhub.auth.features.requests.create.model.today

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
    val validTo: LocalDate,
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

class ChangeOfSupplierBusinessHandler() : RequestBusinessHandler, DocumentBusinessHandler {

    override fun validateAndReturnRequestCommand(createRequestModel: CreateRequestModel): Either<ChangeOfSupplierValidationError, RequestCommand> = either {
        val model = createRequestModel.toChangeOfSupplierBusinessModel()
        validate(model).bind().toRequestCommand()
    }

    override fun getCreateGrantProperties(request: AuthorizationRequest): CreateGrantProperties = CreateGrantProperties(
        validTo = defaultRequestValidTo(),
        validFrom = today()
    )

    override fun validateAndReturnDocumentCommand(model: CreateDocumentModel): Either<CreateDocumentError, DocumentCommand> = either {
        val model = model.toChangeOfSupplierBusinessModel()
        validate(model)
            .mapLeft { raise(CreateDocumentError.MappingError) }
            .bind()
            .toDocumentCommand()
    }

    override fun getCreateGrantProperties(document: AuthorizationDocument): CreateGrantProperties = CreateGrantProperties(
        validTo = defaultRequestValidTo(),
        validFrom = today()
    )

    private fun validate(model: ChangeOfSupplierBusinessModel): Either<ChangeOfSupplierValidationError, ChangeOfSupplierBusinessCommand> {
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
            validTo = defaultRequestValidTo(),
            meta = meta,
        ).right()
    }
}
