package no.elhub.auth.features.requests.create

import arrow.core.Either
import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import no.elhub.auth.features.common.PartyService
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestPropertiesRepository
import no.elhub.auth.features.requests.common.RequestRepository
import no.elhub.auth.features.requests.create.command.toRequestProperties
import no.elhub.auth.features.requests.create.model.CreateRequestModel
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeOrchestrator
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeValidationError
import no.elhub.devxp.jsonapi.response.JsonApiErrorObject

class Handler(
    private val requestTypeOrchestrator: RequestTypeOrchestrator,
    private val partyService: PartyService,
    private val requestRepo: RequestRepository,
    private val requestPropertyRepo: RequestPropertiesRepository,
) {
    suspend operator fun invoke(model: CreateRequestModel): Either<CreateRequestError, AuthorizationRequest> {
        val requestTypeHandler = requestTypeOrchestrator.resolve(model.requestType)

        val command =
            requestTypeHandler
                .handle(model)
                .getOrElse { validationError ->
                    return Either.Left(CreateRequestError.ValidationError(validationError))
                }

        val requestedFromParty =
            partyService
                .resolve(command.requestedFrom)
                .getOrElse { return Either.Left(CreateRequestError.RequestedFromPartyError) }

        val requestedByParty =
            partyService
                .resolve(command.requestedBy)
                .getOrElse { return Either.Left(CreateRequestError.RequestedByPartyError) }

        val metaAttributes = command.meta.toMetaAttributes()

        val requestedToParty =
            partyService
                .resolve(command.requestedTo)
                .getOrElse { return Either.Left(CreateRequestError.RequestedByPartyError) }

        val requestToCreate =
            AuthorizationRequest.create(
                type = command.type,
                requestedFrom = requestedFromParty,
                requestedBy = requestedByParty,
                requestedTo = requestedToParty,
                validTo = command.validTo,
            )

        val savedRequest =
            requestRepo
                .insert(requestToCreate)
                .getOrElse { return Either.Left(CreateRequestError.PersistenceError) }

        val requestProperties = metaAttributes.toRequestProperties(savedRequest.id)

        requestPropertyRepo.insert(requestProperties).getOrElse {
            return Either.Left(CreateRequestError.PersistenceError)
        }

        return Either.Right(savedRequest.copy(properties = metaAttributes))
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
