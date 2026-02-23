package no.elhub.auth.features.businessprocesses.moveinandchangeofenergysupplier

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.businessprocesses.datasharing.StromprisService
import no.elhub.auth.features.businessprocesses.moveinandchangeofenergysupplier.domain.MoveInAndChangeOfEnergySupplierBusinessCommand
import no.elhub.auth.features.businessprocesses.moveinandchangeofenergysupplier.domain.MoveInAndChangeOfEnergySupplierBusinessMeta
import no.elhub.auth.features.businessprocesses.moveinandchangeofenergysupplier.domain.MoveInAndChangeOfEnergySupplierBusinessModel
import no.elhub.auth.features.businessprocesses.moveinandchangeofenergysupplier.domain.toDocumentCommand
import no.elhub.auth.features.businessprocesses.moveinandchangeofenergysupplier.domain.toMoveInAndChangeOfEnergySupplierBusinessModel
import no.elhub.auth.features.businessprocesses.moveinandchangeofenergysupplier.domain.toRequestCommand
import no.elhub.auth.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.AccessType.OWNED
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsService
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsService
import no.elhub.auth.features.businessprocesses.structuredata.organisations.PartyStatus
import no.elhub.auth.features.businessprocesses.structuredata.organisations.PartyType
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.person.PersonService
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentBusinessHandler
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
private const val REGEX_REQUESTED_BY = "^\\d{13}$"
private const val REGEX_METERING_POINT = "^\\d{18}$"

private const val MOVE_IN_REQUEST_VALID_DAYS = 28
private const val MOVE_IN_GRANT_VALID_YEARS = 1

private fun moveInRequestValidTo() = today().plus(DatePeriod(days = MOVE_IN_REQUEST_VALID_DAYS))

private fun moveInGrantValidTo() = today().plus(DatePeriod(years = MOVE_IN_GRANT_VALID_YEARS))

class MoveInAndChangeOfEnergySupplierBusinessHandler(
    private val organisationsService: OrganisationsService,
    private val meteringPointsService: MeteringPointsService,
    private val personService: PersonService,
    private val stromprisService: StromprisService,
    private val validateBalanceSupplierContractName: Boolean
) : RequestBusinessHandler,
    DocumentBusinessHandler {
    override suspend fun validateAndReturnRequestCommand(createRequestModel: CreateRequestModel): Either<BusinessProcessError, RequestCommand> =
        either {
            val model = createRequestModel.toMoveInAndChangeOfEnergySupplierBusinessModel()
            validate(model).mapLeft { it.toBusinessError() }.bind().toRequestCommand()
        }

    override fun getCreateGrantProperties(request: AuthorizationRequest): CreateGrantProperties =
        CreateGrantProperties(
            validTo = moveInGrantValidTo(),
            validFrom = today(),
        )

    override suspend fun validateAndReturnDocumentCommand(model: CreateDocumentModel): Either<BusinessProcessError, DocumentCommand> =
        either {
            val businessModel = model.toMoveInAndChangeOfEnergySupplierBusinessModel()
            validate(businessModel).mapLeft { it.toBusinessError() }.bind().toDocumentCommand()
        }

    override fun getCreateGrantProperties(document: AuthorizationDocument): CreateGrantProperties =
        CreateGrantProperties(
            validTo = moveInGrantValidTo(),
            validFrom = today(),
        )

    private suspend fun validate(
        model: MoveInAndChangeOfEnergySupplierBusinessModel
    ): Either<MoveInAndChangeOfEnergySupplierValidationError, MoveInAndChangeOfEnergySupplierBusinessCommand> {
        if (model.requestedFromName.isBlank()) {
            return MoveInAndChangeOfEnergySupplierValidationError.MissingRequestedFromName.left()
        }

        if (model.balanceSupplierName.isBlank()) {
            return MoveInAndChangeOfEnergySupplierValidationError.MissingBalanceSupplierName.left()
        }

        if (model.balanceSupplierContractName.isBlank()) {
            return MoveInAndChangeOfEnergySupplierValidationError.MissingBalanceSupplierContractName.left()
        }

        if (model.requestedForMeteringPointId.isBlank()) {
            return MoveInAndChangeOfEnergySupplierValidationError.MissingMeteringPointId.left()
        }

        if (!model.requestedForMeteringPointId.matches(Regex(REGEX_METERING_POINT))) {
            return MoveInAndChangeOfEnergySupplierValidationError.InvalidMeteringPointId.left()
        }

        if (model.requestedForMeteringPointAddress.isBlank()) {
            return MoveInAndChangeOfEnergySupplierValidationError.MissingMeteringPointAddress.left()
        }

        if (model.requestedFrom.idValue.isBlank()) {
            return MoveInAndChangeOfEnergySupplierValidationError.MissingRequestedFrom.left()
        }

        if (!model.requestedFrom.idValue.matches(Regex(REGEX_REQUESTED_FROM))) {
            return MoveInAndChangeOfEnergySupplierValidationError.InvalidRequestedFrom.left()
        }

        // temporary mapping until model has elhubInternalId instead of NIN
        val endUserElhubInternalId = personService.findOrCreateByNin(model.requestedFrom.idValue).getOrNull()?.internalId
            ?: return MoveInAndChangeOfEnergySupplierValidationError.RequestedFromNotFound.left()

        val meteringPoint = meteringPointsService.getMeteringPointByIdAndElhubInternalId(
            meteringPointId = model.requestedForMeteringPointId,
            elhubInternalId = endUserElhubInternalId.toString()
        ).mapLeft { err ->
            return when (err) {
                ClientError.NotFound -> MoveInAndChangeOfEnergySupplierValidationError.MeteringPointNotFound.left()

                ClientError.BadRequest,
                ClientError.Unauthorized,
                ClientError.ServerError,
                is ClientError.UnexpectedError -> MoveInAndChangeOfEnergySupplierValidationError.UnexpectedError.left()
            }
        }.getOrElse { return MoveInAndChangeOfEnergySupplierValidationError.MeteringPointNotFound.left() }

        if (meteringPoint.data.relationships.endUser != null && meteringPoint.data.attributes?.accessType == OWNED) {
            return MoveInAndChangeOfEnergySupplierValidationError.RequestedFromIsMeteringPointEndUser.left()
        }

        val startDate = model.startDate
        startDate?.let {
            if (it > today()) {
                return MoveInAndChangeOfEnergySupplierValidationError.StartDateNotBackInTime.left()
            }
        }

        if (model.requestedBy.idValue.isBlank()) {
            return MoveInAndChangeOfEnergySupplierValidationError.MissingRequestedBy.left()
        }

        if (!model.requestedBy.idValue.matches(Regex(REGEX_REQUESTED_BY))) {
            return MoveInAndChangeOfEnergySupplierValidationError.InvalidRequestedBy.left()
        }

        val party = organisationsService.getPartyByIdAndPartyType(model.requestedBy.idValue, PartyType.BalanceSupplier)
            .mapLeft { err ->
                return when (err) {
                    ClientError.NotFound -> MoveInAndChangeOfEnergySupplierValidationError.RequestedByNotFound.left()

                    ClientError.BadRequest,
                    ClientError.Unauthorized,
                    ClientError.ServerError,
                    is ClientError.UnexpectedError -> MoveInAndChangeOfEnergySupplierValidationError.UnexpectedError.left()
                }
            }.getOrElse { return MoveInAndChangeOfEnergySupplierValidationError.RequestedByNotFound.left() }

        if (party.data.attributes?.status != PartyStatus.ACTIVE) {
            return MoveInAndChangeOfEnergySupplierValidationError.NotActiveRequestedBy.left()
        }

        if (model.requestedTo.idValue != model.requestedFrom.idValue) {
            return MoveInAndChangeOfEnergySupplierValidationError.RequestedToRequestedFromMismatch.left()
        }

        if (validateBalanceSupplierContractName) {
            val organizationNumber =
                party.data.relationships.organizationNumber?.data?.id ?: return MoveInAndChangeOfEnergySupplierValidationError.UnexpectedError.left()
            val products = stromprisService.getProductsByOrganizationNumber(organizationNumber).mapLeft { err ->
                return when (err) {
                    ClientError.NotFound -> MoveInAndChangeOfEnergySupplierValidationError.ContractsNotFound.left()

                    ClientError.BadRequest,
                    ClientError.Unauthorized,
                    ClientError.ServerError,
                    is ClientError.UnexpectedError -> MoveInAndChangeOfEnergySupplierValidationError.UnexpectedError.left()
                }
            }.getOrElse { return MoveInAndChangeOfEnergySupplierValidationError.UnexpectedError.left() }
            if (products.data.none { product ->
                    model.balanceSupplierContractName.equals(product.attributes?.name?.trim(), ignoreCase = true)
                }
            ) {
                return MoveInAndChangeOfEnergySupplierValidationError.InvalidBalanceSupplierContractName.left()
            }
        }

        val meta =
            MoveInAndChangeOfEnergySupplierBusinessMeta(
                language = model.language,
                requestedFromName = model.requestedFromName,
                requestedForMeteringPointId = model.requestedForMeteringPointId,
                requestedForMeteringPointAddress = model.requestedForMeteringPointAddress,
                requestedForMeterNumber = meteringPoint.data.attributes?.accountingPoint?.meter?.meterNumber ?: "",
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

        return MoveInAndChangeOfEnergySupplierBusinessCommand(
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
