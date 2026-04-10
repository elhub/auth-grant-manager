package no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.businessprocesses.datasharing.StromprisService
import no.elhub.auth.features.businessprocesses.ediel.EdielEnvironment
import no.elhub.auth.features.businessprocesses.ediel.EdielService
import no.elhub.auth.features.businessprocesses.ediel.RedirectUriDomainValidationResult
import no.elhub.auth.features.businessprocesses.ediel.redirectUriFor
import no.elhub.auth.features.businessprocesses.ediel.validateRedirectUriDomain
import no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier.domain.MoveInAndChangeOfBalanceSupplierBusinessCommand
import no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier.domain.MoveInAndChangeOfBalanceSupplierBusinessMeta
import no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier.domain.MoveInAndChangeOfBalanceSupplierBusinessModel
import no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier.domain.toDocumentCommand
import no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier.domain.toMoveInAndChangeOfBalanceSupplierBusinessModel
import no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier.domain.toRequestCommand
import no.elhub.auth.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.AccessType.OWNED
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsService
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsService
import no.elhub.auth.features.businessprocesses.structuredata.organisations.PartyStatus
import no.elhub.auth.features.businessprocesses.structuredata.organisations.PartyType
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.todayOslo
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.CreateDocumentBusinessModel
import no.elhub.auth.features.documents.common.DocumentBusinessHandler
import no.elhub.auth.features.documents.create.command.DocumentCommand
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.common.CreateGrantProperties
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.CreateRequestBusinessModel
import no.elhub.auth.features.requests.common.RequestBusinessHandler
import no.elhub.auth.features.requests.create.command.RequestCommand

private const val REGEX_NUMBERS_LETTERS_SYMBOLS = "^[a-zA-Z0-9_.-]*$"
private const val REGEX_REQUESTED_FROM = REGEX_NUMBERS_LETTERS_SYMBOLS
private const val REGEX_REQUESTED_BY = "^\\d{13}$"
private const val REGEX_METERING_POINT = "^\\d{18}$"

private const val MOVE_IN_REQUEST_VALID_DAYS = 28
private const val MOVE_IN_GRANT_VALID_YEARS = 1

private val ALLOWED_GRANT_PROPERTY_KEYS = setOf("moveInDate")

private fun moveInRequestValidTo() = todayOslo().plus(DatePeriod(days = MOVE_IN_REQUEST_VALID_DAYS))

private fun moveInGrantValidTo() = todayOslo().plus(DatePeriod(years = MOVE_IN_GRANT_VALID_YEARS))

class MoveInAndChangeOfBalanceSupplierBusinessHandler(
    private val organisationsService: OrganisationsService,
    private val meteringPointsService: MeteringPointsService,
    private val stromprisService: StromprisService,
    private val edielService: EdielService,
    private val edielEnvironment: EdielEnvironment,
    private val validateRedirectUriFeature: Boolean,
    private val validateBalanceSupplierContractName: Boolean
) : RequestBusinessHandler,
    DocumentBusinessHandler {

    override suspend fun validateAndReturnRequestCommand(createRequestModel: CreateRequestBusinessModel): Either<BusinessProcessError, RequestCommand> =
        either {
            val model = createRequestModel.toMoveInAndChangeOfBalanceSupplierBusinessModel()
            validateRequest(model).mapLeft { it.toBusinessError() }.bind().toRequestCommand()
        }

    private suspend fun validateRequest(
        model: MoveInAndChangeOfBalanceSupplierBusinessModel
    ): Either<MoveInAndChangeOfBalanceSupplierValidationError, MoveInAndChangeOfBalanceSupplierBusinessCommand> =
        either {
            val command = validate(model).bind()
            if (validateRedirectUriFeature) {
                validateRedirectUriForRequest(model).bind()
            }
            command
        }

    private suspend fun validateRedirectUriForRequest(
        model: MoveInAndChangeOfBalanceSupplierBusinessModel
    ): Either<MoveInAndChangeOfBalanceSupplierValidationError, Unit> {
        val redirectUri = model.redirectURI?.trim()
        // allow null redirect URI as it is an optional field, and the validation of the redirect URI domain should only be performed if a redirect URI is set
        if (redirectUri == null) {
            return Unit.right()
        }

        val redirectUriFromEdiel = edielService.getPartyRedirect(model.requestedBy.id).mapLeft { err ->
            return when (err) {
                ClientError.NotFound -> MoveInAndChangeOfBalanceSupplierValidationError.InvalidRedirectURI.left()

                ClientError.BadRequest,
                ClientError.Unauthorized,
                ClientError.ServerError,
                is ClientError.UnexpectedError -> MoveInAndChangeOfBalanceSupplierValidationError.UnexpectedError.left()
            }
        }.getOrElse { return MoveInAndChangeOfBalanceSupplierValidationError.UnexpectedError.left() }

        return when (validateRedirectUriDomain(redirectUri, redirectUriFromEdiel.redirectUriFor(edielEnvironment))) {
            RedirectUriDomainValidationResult.MatchingDomain -> Unit.right()

            RedirectUriDomainValidationResult.InvalidInputUri -> MoveInAndChangeOfBalanceSupplierValidationError.InvalidRedirectURI.left()

            RedirectUriDomainValidationResult.InvalidEdielUri,
            RedirectUriDomainValidationResult.DomainMismatch -> MoveInAndChangeOfBalanceSupplierValidationError.RedirectURINotMatchingEdiel.left()
        }
    }

    override fun getCreateGrantProperties(request: AuthorizationRequest): CreateGrantProperties {
        val propertyMap = request.properties
            .filter { it.key in ALLOWED_GRANT_PROPERTY_KEYS }
            .associate { it.key to it.value }
        return buildCreateGrantProperties(propertyMap, ALLOWED_GRANT_PROPERTY_KEYS)
    }

    override suspend fun validateAndReturnDocumentCommand(model: CreateDocumentBusinessModel): Either<BusinessProcessError, DocumentCommand> =
        either {
            val businessModel = model.toMoveInAndChangeOfBalanceSupplierBusinessModel()
            validate(businessModel).mapLeft { it.toBusinessError() }.bind().toDocumentCommand()
        }

    override fun getCreateGrantProperties(document: AuthorizationDocument): CreateGrantProperties {
        val propertyMap = document.properties
            .filter { it.key in ALLOWED_GRANT_PROPERTY_KEYS }
            .associate { it.key to it.value }
        return buildCreateGrantProperties(propertyMap, ALLOWED_GRANT_PROPERTY_KEYS)
    }

    private fun buildCreateGrantProperties(
        propertyMap: Map<String, String>,
        allowedKeys: Set<String>
    ): CreateGrantProperties {
        val meta = propertyMap.filterKeys { it in allowedKeys }
        return CreateGrantProperties(
            validTo = moveInGrantValidTo(),
            validFrom = todayOslo(),
            meta = meta
        )
    }

    private suspend fun validate(
        model: MoveInAndChangeOfBalanceSupplierBusinessModel
    ): Either<MoveInAndChangeOfBalanceSupplierValidationError, MoveInAndChangeOfBalanceSupplierBusinessCommand> {
        if (model.requestedFromName.isBlank()) {
            return MoveInAndChangeOfBalanceSupplierValidationError.MissingRequestedFromName.left()
        }

        if (model.balanceSupplierName.isBlank()) {
            return MoveInAndChangeOfBalanceSupplierValidationError.MissingBalanceSupplierName.left()
        }

        if (model.balanceSupplierContractName.isBlank()) {
            return MoveInAndChangeOfBalanceSupplierValidationError.MissingBalanceSupplierContractName.left()
        }

        if (model.requestedForMeteringPointId.isBlank()) {
            return MoveInAndChangeOfBalanceSupplierValidationError.MissingMeteringPointId.left()
        }

        if (!model.requestedForMeteringPointId.matches(Regex(REGEX_METERING_POINT))) {
            return MoveInAndChangeOfBalanceSupplierValidationError.InvalidMeteringPointId.left()
        }

        if (model.requestedForMeteringPointAddress.isBlank()) {
            return MoveInAndChangeOfBalanceSupplierValidationError.MissingMeteringPointAddress.left()
        }

        if (model.requestedFrom.id.isBlank()) {
            return MoveInAndChangeOfBalanceSupplierValidationError.MissingRequestedFrom.left()
        }

        val meteringPoint = meteringPointsService.getMeteringPointByIdAndElhubInternalId(
            meteringPointId = model.requestedForMeteringPointId,
            elhubInternalId = model.requestedFrom.id
        ).mapLeft { err ->
            return when (err) {
                ClientError.NotFound -> MoveInAndChangeOfBalanceSupplierValidationError.MeteringPointNotFound.left()

                ClientError.BadRequest,
                ClientError.Unauthorized,
                ClientError.ServerError,
                is ClientError.UnexpectedError -> MoveInAndChangeOfBalanceSupplierValidationError.UnexpectedError.left()
            }
        }.getOrElse { return MoveInAndChangeOfBalanceSupplierValidationError.MeteringPointNotFound.left() }

        if (meteringPoint.data.relationships.endUser != null && meteringPoint.data.attributes?.accessType == OWNED) {
            return MoveInAndChangeOfBalanceSupplierValidationError.RequestedFromIsMeteringPointEndUser.left()
        }

        val moveInDate = model.moveInDate
        moveInDate?.let {
            if (it > todayOslo()) {
                return MoveInAndChangeOfBalanceSupplierValidationError.MoveInDateNotBackInTime.left()
            }
        }

        if (model.requestedBy.id.isBlank()) {
            return MoveInAndChangeOfBalanceSupplierValidationError.MissingRequestedBy.left()
        }

        if (!model.requestedBy.id.matches(Regex(REGEX_REQUESTED_BY))) {
            return MoveInAndChangeOfBalanceSupplierValidationError.InvalidRequestedBy.left()
        }

        val party = organisationsService.getPartyByIdAndPartyType(
            model.requestedBy.id,
            PartyType.BalanceSupplier
        )
            .mapLeft { err ->
                return when (err) {
                    ClientError.NotFound -> MoveInAndChangeOfBalanceSupplierValidationError.RequestedByNotFound.left()

                    ClientError.BadRequest,
                    ClientError.Unauthorized,
                    ClientError.ServerError,
                    is ClientError.UnexpectedError -> MoveInAndChangeOfBalanceSupplierValidationError.UnexpectedError.left()
                }
            }
            .getOrElse { return MoveInAndChangeOfBalanceSupplierValidationError.RequestedByNotFound.left() }

        if (party.data.attributes?.status != PartyStatus.ACTIVE) {
            return MoveInAndChangeOfBalanceSupplierValidationError.NotActiveRequestedBy.left()
        }

        if (model.requestedTo.id != model.requestedFrom.id) {
            return MoveInAndChangeOfBalanceSupplierValidationError.RequestedToRequestedFromMismatch.left()
        }

        if (validateBalanceSupplierContractName) {
            val organizationNumber =
                party.data.relationships.organizationNumber?.data?.id
                    ?: return MoveInAndChangeOfBalanceSupplierValidationError.UnexpectedError.left()
            val products =
                stromprisService.getProductsByOrganizationNumber(organizationNumber).mapLeft { err ->
                    return when (err) {
                        ClientError.NotFound -> MoveInAndChangeOfBalanceSupplierValidationError.ContractsNotFound.left()

                        ClientError.BadRequest,
                        ClientError.Unauthorized,
                        ClientError.ServerError,
                        is ClientError.UnexpectedError -> MoveInAndChangeOfBalanceSupplierValidationError.UnexpectedError.left()
                    }
                }
                    .getOrElse { return MoveInAndChangeOfBalanceSupplierValidationError.UnexpectedError.left() }
            if (products.data.none { product ->
                    model.balanceSupplierContractName.equals(
                        product.attributes?.name?.trim(),
                        ignoreCase = true
                    )
                }
            ) {
                return MoveInAndChangeOfBalanceSupplierValidationError.InvalidBalanceSupplierContractName.left()
            }
        }

        val meta =
            MoveInAndChangeOfBalanceSupplierBusinessMeta(
                language = model.language,
                requestedFromName = model.requestedFromName,
                requestedForMeteringPointId = model.requestedForMeteringPointId,
                requestedForMeteringPointAddress = model.requestedForMeteringPointAddress,
                requestedForMeterNumber = meteringPoint.data.attributes?.accountingPoint?.meter?.meterNumber
                    ?: "",
                balanceSupplierContractName = model.balanceSupplierContractName,
                balanceSupplierName = model.balanceSupplierName,
                moveInDate = moveInDate,
                redirectURI = model.redirectURI,
            )

        val scopes = listOf(
            CreateScopeData(
                authorizedResourceType = AuthorizationScope.AuthorizationResource.MeteringPoint,
                authorizedResourceId = model.requestedForMeteringPointId,
                permissionType = AuthorizationScope.PermissionType.MoveInAndChangeOfBalanceSupplierForPerson
            )
        )

        return MoveInAndChangeOfBalanceSupplierBusinessCommand(
            validTo = moveInRequestValidTo(),
            scopes = scopes,
            meta = meta,
        ).right()
    }
}
