package no.elhub.auth.features.businessprocesses.movein

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import no.elhub.auth.features.businessprocesses.movein.domain.MoveInBusinessCommand
import no.elhub.auth.features.businessprocesses.movein.domain.MoveInBusinessMeta
import no.elhub.auth.features.businessprocesses.movein.domain.MoveInBusinessModel
import no.elhub.auth.features.businessprocesses.movein.domain.toDocumentCommand
import no.elhub.auth.features.businessprocesses.movein.domain.toMoveInBusinessModel
import no.elhub.auth.features.businessprocesses.movein.domain.toRequestCommand
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentBusinessHandler
import no.elhub.auth.features.documents.create.CreateError
import no.elhub.auth.features.documents.create.command.DocumentCommand
import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.common.CreateGrantProperties
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.RequestBusinessHandler
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.model.CreateRequestModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val REGEX_NUMBERS_LETTERS_SYMBOLS = "^[a-zA-Z0-9_.-]*$"
private const val REGEX_REQUESTED_FROM = REGEX_NUMBERS_LETTERS_SYMBOLS
private const val REGEX_REQUESTED_BY = REGEX_NUMBERS_LETTERS_SYMBOLS
private const val REGEX_METERING_POINT = "^\\d{18}$"

private const val MOVE_IN_REQUEST_VALID_DAYS = 28
private const val MOVE_IN_GRANT_VALID_YEARS = 1

private fun moveInRequestValidTo() = today().plus(DatePeriod(days = MOVE_IN_REQUEST_VALID_DAYS))

private fun moveInGrantValidTo() = today().plus(DatePeriod(years = MOVE_IN_GRANT_VALID_YEARS))

class MoveInBusinessHandler :
    RequestBusinessHandler,
    DocumentBusinessHandler {
    override suspend fun validateAndReturnRequestCommand(createRequestModel: CreateRequestModel): Either<MoveInValidationError, RequestCommand> =
        either {
            val model = createRequestModel.toMoveInBusinessModel()
            validate(model).bind().toRequestCommand()
        }

    override fun getCreateGrantProperties(request: AuthorizationRequest): CreateGrantProperties =
        CreateGrantProperties(
            validTo = moveInGrantValidTo(),
            validFrom = today(),
        )

    override suspend fun validateAndReturnDocumentCommand(model: CreateDocumentModel): Either<CreateError.BusinessValidationError, DocumentCommand> =
        either {
            val model = model.toMoveInBusinessModel()
            validate(model)
                .mapLeft { raise(CreateError.BusinessValidationError(it.message)) }
                .bind()
                .toDocumentCommand()
        }

    override fun getCreateGrantProperties(document: AuthorizationDocument): CreateGrantProperties =
        CreateGrantProperties(
            validTo = moveInGrantValidTo(),
            validFrom = today(),
        )

    private fun validate(model: MoveInBusinessModel): Either<MoveInValidationError, MoveInBusinessCommand> {
        if (model.requestedFromName.isBlank()) {
            return MoveInValidationError.MissingRequestedFromName.left()
        }

        if (model.balanceSupplierName.isBlank()) {
            return MoveInValidationError.MissingBalanceSupplierName.left()
        }

        if (model.balanceSupplierContractName.isBlank()) {
            return MoveInValidationError.MissingBalanceSupplierContractName.left()
        }

        if (model.requestedForMeteringPointId.isBlank()) {
            return MoveInValidationError.MissingMeteringPointId.left()
        }

        if (!model.requestedForMeteringPointId.matches(Regex(REGEX_METERING_POINT))) {
            return MoveInValidationError.InvalidMeteringPointId.left()
        }

        if (model.requestedForMeteringPointAddress.isBlank()) {
            return MoveInValidationError.MissingMeteringPointAddress.left()
        }

        if (model.requestedBy.idValue.isBlank()) {
            return MoveInValidationError.MissingRequestedBy.left()
        }

        if (!model.requestedBy.idValue.matches(Regex(REGEX_REQUESTED_BY))) {
            return MoveInValidationError.InvalidRequestedBy.left()
        }

        if (model.requestedFrom.idValue.isBlank()) {
            return MoveInValidationError.MissingRequestedFrom.left()
        }

        if (!model.requestedFrom.idValue.matches(Regex(REGEX_REQUESTED_FROM))) {
            return MoveInValidationError.InvalidRequestedFrom.left()
        }

        val startDate = model.startDate
        startDate?.let {
            if (it > today()) {
                return MoveInValidationError.StartDateNotBackInTime.left()
            }
        }

        val meta =
            MoveInBusinessMeta(
                requestedFromName = model.requestedFromName,
                requestedForMeteringPointId = model.requestedForMeteringPointId,
                requestedForMeteringPointAddress = model.requestedForMeteringPointAddress,
                balanceSupplierContractName = model.balanceSupplierContractName,
                balanceSupplierName = model.balanceSupplierName,
                startDate = startDate,
                redirectURI = model.redirectURI,
            )

        val scopes = listOf(
            CreateScopeData(
                authorizedResourceType = AuthorizationScope.AuthorizationResource.MeteringPoint,
                authorizedResourceId = model.requestedForMeteringPointId,
                permissionType = AuthorizationScope.PermissionType.MoveInAndChangeOfEnergySupplierForPerson
            )
        )

        return MoveInBusinessCommand(
            requestedFrom = model.requestedFrom,
            requestedBy = model.requestedBy,
            requestedTo = model.requestedTo,
            validTo = moveInRequestValidTo(),
            scopes = scopes,
            meta = meta,
        ).right()
    }
}

@OptIn(ExperimentalTime::class)
fun today(): LocalDate {
    val now = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
    return now
}
