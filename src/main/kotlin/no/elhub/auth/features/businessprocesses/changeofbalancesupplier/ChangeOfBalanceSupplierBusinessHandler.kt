package no.elhub.auth.features.businessprocesses.changeofbalancesupplier

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.domain.ChangeOfBalanceSupplierBusinessCommand
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.domain.ChangeOfBalanceSupplierBusinessMeta
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.domain.ChangeOfBalanceSupplierBusinessModel
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.domain.toChangeOfBalanceSupplierBusinessModel
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.domain.toDocumentCommand
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.domain.toRequestCommand
import no.elhub.auth.features.businessprocesses.datasharing.StromprisService
import no.elhub.auth.features.businessprocesses.ediel.EdielEnvironment
import no.elhub.auth.features.businessprocesses.ediel.EdielService
import no.elhub.auth.features.businessprocesses.ediel.RedirectUriDomainValidationResult
import no.elhub.auth.features.businessprocesses.ediel.redirectUriFor
import no.elhub.auth.features.businessprocesses.ediel.validateRedirectUriDomain
import no.elhub.auth.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.AccessType.SHARED
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsService
import no.elhub.auth.features.businessprocesses.structuredata.organisations.OrganisationsService
import no.elhub.auth.features.businessprocesses.structuredata.organisations.PartyStatus
import no.elhub.auth.features.businessprocesses.structuredata.organisations.PartyType
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.person.PersonService
import no.elhub.auth.features.common.todayOslo
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

private const val REGEX_NUMBERS_LETTERS_SYMBOLS = "^[a-zA-Z0-9_.-]*$"
private const val REGEX_REQUESTED_FROM = REGEX_NUMBERS_LETTERS_SYMBOLS
private const val REGEX_REQUESTED_BY = "^\\d{13}$"
private const val REGEX_METERING_POINT = "^\\d{18}$"

private const val CHANGE_OF_BALANCE_SUPPLIER_REQUEST_VALID_DAYS = 28
private const val CHANGE_OF_BALANCE_SUPPLIER_GRANT_VALID_YEARS = 1

private fun changeOfBalanceSupplierRequestValidTo() =
    todayOslo().plus(DatePeriod(days = CHANGE_OF_BALANCE_SUPPLIER_REQUEST_VALID_DAYS))

private fun changeOfBalanceSupplierGrantValidTo() =
    todayOslo().plus(DatePeriod(years = CHANGE_OF_BALANCE_SUPPLIER_GRANT_VALID_YEARS))

class ChangeOfBalanceSupplierBusinessHandler(
    private val meteringPointsService: MeteringPointsService,
    private val personService: PersonService,
    private val organisationsService: OrganisationsService,
    private val stromprisService: StromprisService,
    private val edielService: EdielService,
    private val edielEnvironment: EdielEnvironment,
    private val validateRedirectUriFeature: Boolean,
    private val validateBalanceSupplierContractName: Boolean
) : RequestBusinessHandler, DocumentBusinessHandler {

    override suspend fun validateAndReturnRequestCommand(createRequestModel: CreateRequestModel): Either<BusinessProcessError, RequestCommand> =
        either {
            val model = createRequestModel.toChangeOfBalanceSupplierBusinessModel()
            validateRequest(model)
                .mapLeft { it.toBusinessError() }
                .bind()
                .toRequestCommand()
        }

    private suspend fun validateRequest(
        model: ChangeOfBalanceSupplierBusinessModel
    ): Either<ChangeOfBalanceSupplierValidationError, ChangeOfBalanceSupplierBusinessCommand> =
        either {
            val command = validate(model).bind()
            if (validateRedirectUriFeature) {
                validateRedirectUriForRequest(model).bind()
            }
            command
        }

    private suspend fun validateRedirectUriForRequest(
        model: ChangeOfBalanceSupplierBusinessModel
    ): Either<ChangeOfBalanceSupplierValidationError, Unit> {
        val redirectUri = model.redirectURI?.trim()
        // allow null redirect URI as it is an optional field, and the validation of the redirect URI domain should only be performed if a redirect URI is set
        if (redirectUri == null) {
            return Unit.right()
        }

        val redirectUriFromEdiel = edielService.getPartyRedirect(model.requestedBy.idValue).mapLeft { err ->
            return when (err) {
                ClientError.NotFound -> ChangeOfBalanceSupplierValidationError.InvalidRedirectURI.left()

                ClientError.BadRequest,
                ClientError.Unauthorized,
                ClientError.ServerError,
                is ClientError.UnexpectedError -> ChangeOfBalanceSupplierValidationError.UnexpectedError.left()
            }
        }.getOrElse { return ChangeOfBalanceSupplierValidationError.UnexpectedError.left() }

        return when (validateRedirectUriDomain(redirectUri, redirectUriFromEdiel.redirectUriFor(edielEnvironment))) {
            RedirectUriDomainValidationResult.MatchingDomain -> Unit.right()

            RedirectUriDomainValidationResult.InvalidInputUri -> ChangeOfBalanceSupplierValidationError.InvalidRedirectURI.left()

            RedirectUriDomainValidationResult.InvalidEdielUri,
            RedirectUriDomainValidationResult.DomainMismatch -> ChangeOfBalanceSupplierValidationError.RedirectURINotMatchingEdiel.left()
        }
    }

    override fun getCreateGrantProperties(request: AuthorizationRequest): CreateGrantProperties =
        CreateGrantProperties(
            validTo = changeOfBalanceSupplierGrantValidTo(),
            validFrom = todayOslo()
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
            validTo = changeOfBalanceSupplierGrantValidTo(),
            validFrom = todayOslo(),
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
        val endUserElhubInternalId =
            personService.findOrCreateByNin(model.requestedFrom.idValue).getOrNull()?.internalId
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

        if (model.requestedTo.idValue != model.requestedFrom.idValue) {
            return ChangeOfBalanceSupplierValidationError.RequestedToRequestedFromMismatch.left()
        }

        if (validateBalanceSupplierContractName) {
            val organizationNumber =
                party.data.relationships.organizationNumber?.data?.id
                    ?: return ChangeOfBalanceSupplierValidationError.UnexpectedError.left()
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
            validTo = changeOfBalanceSupplierRequestValidTo(),
            scopes = scopes,
            meta = meta,
        ).right()
    }
}
