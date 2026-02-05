package no.elhub.auth.features.businessprocesses.changeofsupplier

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import no.elhub.auth.features.businessprocesses.changeofsupplier.domain.ChangeOfSupplierBusinessCommand
import no.elhub.auth.features.businessprocesses.changeofsupplier.domain.ChangeOfSupplierBusinessMeta
import no.elhub.auth.features.businessprocesses.changeofsupplier.domain.ChangeOfSupplierBusinessModel
import no.elhub.auth.features.businessprocesses.changeofsupplier.domain.toChangeOfSupplierBusinessModel
import no.elhub.auth.features.businessprocesses.changeofsupplier.domain.toDocumentCommand
import no.elhub.auth.features.businessprocesses.changeofsupplier.domain.toRequestCommand
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.AccessType.SHARED
import no.elhub.auth.features.businessprocesses.structuredata.meteringpoints.MeteringPointsService
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.person.PersonService
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

class ChangeOfSupplierBusinessHandler(
    private val meteringPointsService: MeteringPointsService,
    private val personService: PersonService
) : RequestBusinessHandler, DocumentBusinessHandler {
    override suspend fun validateAndReturnRequestCommand(createRequestModel: CreateRequestModel): Either<ChangeOfSupplierValidationError, RequestCommand> =
        either {
            val model = createRequestModel.toChangeOfSupplierBusinessModel()
            validate(model).bind().toRequestCommand()
        }

    override fun getCreateGrantProperties(request: AuthorizationRequest): CreateGrantProperties =
        CreateGrantProperties(
            validTo = defaultValidTo(),
            validFrom = today(),
        )

    override suspend fun validateAndReturnDocumentCommand(model: CreateDocumentModel): Either<CreateError.BusinessValidationError, DocumentCommand> =
        either {
            val model = model.toChangeOfSupplierBusinessModel()
            validate(model)
                .mapLeft { raise(CreateError.BusinessValidationError(it.message)) }
                .bind()
                .toDocumentCommand()
        }

    override fun getCreateGrantProperties(document: AuthorizationDocument): CreateGrantProperties =
        CreateGrantProperties(
            validTo = defaultValidTo(),
            validFrom = today(),
        )

    private suspend fun validate(model: ChangeOfSupplierBusinessModel): Either<ChangeOfSupplierValidationError, ChangeOfSupplierBusinessCommand> {
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

        // temporary mapping until model has elhubInternalId instead of NIN
        val endUserElhubInternalId = personService.findOrCreateByNin(model.requestedTo.idValue).getOrNull()?.internalId
            ?: return ChangeOfSupplierValidationError.RequestedToNotFound.left()

        val meteringPoint = meteringPointsService.getMeteringPointByIdAndElhubInternalId(
            meteringPointId = model.requestedForMeteringPointId,
            elhubInternalId = endUserElhubInternalId.toString()
        )

        if (meteringPoint.isLeft()) {
            return ChangeOfSupplierValidationError.MeteringPointNotFound.left()
        }
        val meteringPointResponse = meteringPoint.getOrNull()!!

        if (meteringPointResponse.data.relationships.endUser == null || meteringPointResponse.data.attributes?.accessType == SHARED) {
            return ChangeOfSupplierValidationError.RequestedToNotMeteringPointEndUser.left()
        }

        // temporary validation for URI until it is fetched from EDIEL and validated against that value instead
        if (model.redirectURI != null && !model.redirectURI.contains("http")) {
            return ChangeOfSupplierValidationError.InvalidRedirectURI.left()
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
                redirectURI = model.redirectURI,
            )

        val scopes = listOf(
            CreateScopeData(
                authorizedResourceType = AuthorizationScope.AuthorizationResource.MeteringPoint,
                authorizedResourceId = model.requestedForMeteringPointId,
                permissionType = AuthorizationScope.PermissionType.ChangeOfEnergySupplierForPerson
            )
        )

        return ChangeOfSupplierBusinessCommand(
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
