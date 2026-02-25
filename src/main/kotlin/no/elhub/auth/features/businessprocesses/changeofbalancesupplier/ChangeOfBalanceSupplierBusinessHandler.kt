package no.elhub.auth.features.businessprocesses.changeofenergysupplier

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
import no.elhub.auth.features.businessprocesses.changeofenergysupplier.domain.ChangeOfBalanceSupplierBusinessCommand
import no.elhub.auth.features.businessprocesses.changeofenergysupplier.domain.ChangeOfBalanceSupplierBusinessMeta
import no.elhub.auth.features.businessprocesses.changeofenergysupplier.domain.ChangeOfBalanceSupplierBusinessModel
import no.elhub.auth.features.businessprocesses.changeofenergysupplier.domain.toChangeOfBalanceSupplierBusinessModel
import no.elhub.auth.features.businessprocesses.changeofenergysupplier.domain.toDocumentCommand
import no.elhub.auth.features.businessprocesses.changeofenergysupplier.domain.toRequestCommand
import no.elhub.auth.features.businessprocesses.datasharing.StromprisService
import no.elhub.auth.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.AccessType.SHARED
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

class ChangeOfBalanceSupplierBusinessHandler(
    private val meteringPointsService: MeteringPointsService,
    private val personService: PersonService,
    private val organisationsService: OrganisationsService,
    private val stromprisService: StromprisService,
    private val validateBalanceSupplierContractName: Boolean
) : RequestBusinessHandler, DocumentBusinessHandler {
    override suspend fun validateAndReturnRequestCommand(createRequestModel: CreateRequestModel): Either<BusinessProcessError, RequestCommand> =
        either {
            val model = createRequestModel.toChangeOfBalanceSupplierBusinessModel()
            validate(model)
                .mapLeft { it.toBusinessError() }
                .bind()
                .toRequestCommand()
        }

    override fun getCreateGrantProperties(request: AuthorizationRequest): CreateGrantProperties =
        CreateGrantProperties(
            validTo = defaultValidTo(),
            validFrom = today()
        )

    override suspend fun validateAndReturnDocumentCommand(model: CreateDocumentModel): Either<BusinessProcessError, DocumentCommand> =
        either {
            val businessModel = model.toChangeOfBalanceSupplierBusinessModel()
            validate(businessModel)
                .mapLeft { it.toBusinessError() }
                .bind()
                .toDocumentCommand()
        }

    override fun getCreateGrantProperties(document: AuthorizationDocument): CreateGrantProperties =
        CreateGrantProperties(
            validTo = defaultValidTo(),
            validFrom = today(),
        )

    private suspend fun validate(
        model: ChangeOfBalanceSupplierBusinessModel
    ): Either<ChangeOfBalanceSupplierValidationError, ChangeOfBalanceSupplierBusinessCommand> {
        if (model.requestedFromName.isBlank()) {
            return ChangeOfBalanceSupplierValidationError.MissingRequestedFromName.left()
        }

        if (model.balanceSupplierName.isBlank()) {
            return ChangeOfBalanceSupplierValidationError.MissingBalanceSupplierName.left()
        }

        if (model.balanceSupplierContractName.isBlank()) {
            return ChangeOfBalanceSupplierValidationError.MissingBalanceSupplierContractName.left()
        }

        if (model.requestedForMeteringPointId.isBlank()) {
            return ChangeOfBalanceSupplierValidationError.MissingMeteringPointId.left()
        }

        if (!model.requestedForMeteringPointId.matches(Regex(REGEX_METERING_POINT))) {
            return ChangeOfBalanceSupplierValidationError.InvalidMeteringPointId.left()
        }

        if (model.requestedFrom.idValue.isBlank()) {
            return ChangeOfBalanceSupplierValidationError.MissingRequestedFrom.left()
        }

        if (!model.requestedFrom.idValue.matches(Regex(REGEX_REQUESTED_FROM))) {
            return ChangeOfBalanceSupplierValidationError.InvalidRequestedFrom.left()
        }

        // temporary mapping until model has elhubInternalId instead of NIN
        val endUserElhubInternalId = personService.findOrCreateByNin(model.requestedFrom.idValue).getOrNull()?.internalId
            ?: return ChangeOfBalanceSupplierValidationError.RequestedFromNotFound.left()

        val meteringPoint = meteringPointsService.getMeteringPointByIdAndElhubInternalId(
            meteringPointId = model.requestedForMeteringPointId,
            elhubInternalId = endUserElhubInternalId.toString()
        ).mapLeft { err ->
            return when (err) {
                ClientError.NotFound -> ChangeOfBalanceSupplierValidationError.MeteringPointNotFound.left()

                ClientError.BadRequest,
                ClientError.Unauthorized,
                ClientError.ServerError,
                is ClientError.UnexpectedError -> ChangeOfBalanceSupplierValidationError.UnexpectedError.left()
            }
        }.getOrElse { return ChangeOfBalanceSupplierValidationError.MeteringPointNotFound.left() }

        if (meteringPoint.data.relationships.endUser == null || meteringPoint.data.attributes?.accessType == SHARED) {
            return ChangeOfBalanceSupplierValidationError.RequestedFromNotMeteringPointEndUser.left()
        }

        if (meteringPoint.data.attributes?.accountingPoint?.blockedForSwitching == true) {
            return ChangeOfBalanceSupplierValidationError.MeteringPointBlockedForSwitching.left()
        }

        // temporary validation for URI until it is fetched from EDIEL and validated against that value instead
        if (model.redirectURI != null && !model.redirectURI.contains("http")) {
            return ChangeOfBalanceSupplierValidationError.InvalidRedirectURI.left()
        }

        if (model.requestedForMeteringPointAddress.isBlank()) {
            return ChangeOfBalanceSupplierValidationError.MissingMeteringPointAddress.left()
        }

        if (model.requestedBy.idValue.isBlank()) {
            return ChangeOfBalanceSupplierValidationError.MissingRequestedBy.left()
        }

        if (!model.requestedBy.idValue.matches(Regex(REGEX_REQUESTED_BY))) {
            return ChangeOfBalanceSupplierValidationError.InvalidRequestedBy.left()
        }

        val party = organisationsService.getPartyByIdAndPartyType(
            partyId = model.requestedBy.idValue,
            partyType = PartyType.BalanceSupplier
        ).mapLeft { err ->
            return when (err) {
                ClientError.NotFound -> ChangeOfBalanceSupplierValidationError.RequestedByNotFound.left()

                ClientError.BadRequest,
                ClientError.Unauthorized,
                ClientError.ServerError,
                is ClientError.UnexpectedError -> ChangeOfBalanceSupplierValidationError.UnexpectedError.left()
            }
        }.getOrElse { return ChangeOfBalanceSupplierValidationError.RequestedByNotFound.left() }

        if (party.data.attributes?.status != PartyStatus.ACTIVE) {
            return ChangeOfBalanceSupplierValidationError.NotActiveRequestedBy.left()
        }

        val currentBalanceSupplier = meteringPoint.data.attributes?.balanceSupplierContract?.partyFunction
        if (model.requestedBy.idValue == currentBalanceSupplier?.partyId) {
            return ChangeOfBalanceSupplierValidationError.MatchingRequestedBy.left()
        }

        if (model.requestedTo.idValue != model.requestedFrom.idValue) {
            return ChangeOfBalanceSupplierValidationError.RequestedToRequestedFromMismatch.left()
        }

        if (validateBalanceSupplierContractName) {
            val organizationNumber =
                party.data.relationships.organizationNumber?.data?.id ?: return ChangeOfBalanceSupplierValidationError.UnexpectedError.left()
            val products = stromprisService.getProductsByOrganizationNumber(organizationNumber).mapLeft { err ->
                return when (err) {
                    ClientError.NotFound -> ChangeOfBalanceSupplierValidationError.ContractsNotFound.left()

                    ClientError.BadRequest,
                    ClientError.Unauthorized,
                    ClientError.ServerError,
                    is ClientError.UnexpectedError -> ChangeOfBalanceSupplierValidationError.UnexpectedError.left()
                }
            }.getOrElse { return ChangeOfBalanceSupplierValidationError.UnexpectedError.left() }
            if (products.data.none { product ->
                    model.balanceSupplierContractName.equals(product.attributes?.name?.trim(), ignoreCase = true)
                }
            ) {
                return ChangeOfBalanceSupplierValidationError.InvalidBalanceSupplierContractName.left()
            }
        }

        val meta =
            ChangeOfBalanceSupplierBusinessMeta(
                language = model.language,
                requestedFromName = model.requestedFromName,
                requestedForMeteringPointId = model.requestedForMeteringPointId,
                requestedForMeterNumber = meteringPoint.data.attributes?.accountingPoint?.meter?.meterNumber ?: "",
                requestedForMeteringPointAddress = model.requestedForMeteringPointAddress,
                balanceSupplierContractName = model.balanceSupplierContractName,
                balanceSupplierName = model.balanceSupplierName,
                redirectURI = model.redirectURI,
            )

        val scopes = listOf(
            CreateScopeData(
                authorizedResourceType = AuthorizationScope.AuthorizationResource.MeteringPoint,
                authorizedResourceId = model.requestedForMeteringPointId,
                permissionType = AuthorizationScope.PermissionType.ChangeOfBalanceSupplierForPerson
            )
        )

        return ChangeOfBalanceSupplierBusinessCommand(
            requestedFrom = model.requestedFrom,
            requestedBy = model.requestedBy,
            requestedTo = model.requestedTo,
            validTo = defaultValidTo(),
            scopes = scopes,
            meta = meta,
        ).right()
    }
}

@OptIn(ExperimentalTime::class)
fun defaultValidTo(): LocalDate {
    val now = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
    return now.plus(DatePeriod(days = 30))
}

@OptIn(ExperimentalTime::class)
fun today(): LocalDate {
    val now = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
    return now
}
