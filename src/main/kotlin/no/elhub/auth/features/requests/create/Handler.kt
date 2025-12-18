package no.elhub.auth.features.requests.create

import arrow.core.Either
import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierValidationError
import no.elhub.auth.features.common.party.PartyService
import no.elhub.auth.features.grants.common.CreateGrantProperties
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestProperty
import no.elhub.auth.features.requests.common.ProxyRequestBusinessHandler
import no.elhub.auth.features.requests.common.RequestPropertiesRepository
import no.elhub.auth.features.requests.common.RequestRepository
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.model.CreateRequestModel
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeValidationError
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject

class Handler(
    private val proxyRequestBusinessHandler: ProxyRequestBusinessHandler,
    private val partyService: PartyService,
    private val requestRepo: RequestRepository,
    private val requestPropertyRepo: RequestPropertiesRepository,
) {
    suspend operator fun invoke(model: CreateRequestModel): Either<CreateRequestError, AuthorizationRequest> {
        val businessCommand =
            proxyRequestBusinessHandler
                .validateAndReturnRequestCommand(model)
                .getOrElse { validationError ->
                    return Either.Left(CreateRequestError.ValidationError(validationError))
                }

        val requestedFromParty =
            partyService
                .resolve(businessCommand.requestedFrom)
                .getOrElse { return Either.Left(CreateRequestError.RequestedFromPartyError) }

        val requestedByParty =
            partyService
                .resolve(businessCommand.requestedBy)
                .getOrElse { return Either.Left(CreateRequestError.RequestedByPartyError) }

        val metaAttributes = businessCommand.meta.toMetaAttributes()

        val requestedToParty =
            partyService
                .resolve(businessCommand.requestedTo)
                .getOrElse { return Either.Left(CreateRequestError.RequestedByPartyError) }

        val requestToCreate =
            AuthorizationRequest.create(
                type = businessCommand.type,
                requestedFrom = requestedFromParty,
                requestedBy = requestedByParty,
                requestedTo = requestedToParty,
                validTo = businessCommand.validTo,
            )

        val savedRequest =
            requestRepo
                .insert(requestToCreate)
                .getOrElse { return Either.Left(CreateRequestError.PersistenceError) }

        val requestProperties: List<AuthorizationRequestProperty> = metaAttributes.map {
            AuthorizationRequestProperty(
                requestId = savedRequest.id,
                key = it.key,
                value = it.value,
            )
        }

        requestPropertyRepo.insert(requestProperties).getOrElse {
            return Either.Left(CreateRequestError.PersistenceError)
        }

        return Either.Right(savedRequest.copy(properties = requestProperties))
    }
}

sealed class CreateRequestError {
    data object MappingError : CreateRequestError()

    data object PersistenceError : CreateRequestError()

    data object RequestedFromPartyError : CreateRequestError()

    data object RequestedByPartyError : CreateRequestError()

    data class ValidationError(
        val reason: RequestTypeValidationError,
    ) : CreateRequestError()
}

/**
 * Map a domain validation error to an HTTP/JSON:API error.
 */
fun CreateRequestError.ValidationError.toApiErrorResponse(): Pair<HttpStatusCode, JsonApiErrorObject> =
    HttpStatusCode.BadRequest to
        JsonApiErrorObject(
            title = "Validation Error",
            code = this.reason.code,
            status = HttpStatusCode.BadRequest.value.toString(),
            detail = this.reason.message,
        )

interface RequestBusinessHandler {
    fun validateAndReturnRequestCommand(createRequestModel: CreateRequestModel): Either<ChangeOfSupplierValidationError, RequestCommand>
    fun getCreateGrantProperties(request: AuthorizationRequest): CreateGrantProperties
}
