package no.elhub.auth.features.requests.create

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.ktor.http.HttpStatusCode
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
import org.jetbrains.exposed.sql.transactions.transaction

class Handler(
    private val proxyRequestBusinessHandler: ProxyRequestBusinessHandler,
    private val partyService: PartyService,
    private val requestRepo: RequestRepository,
    private val requestPropertyRepo: RequestPropertiesRepository,
) {
    suspend operator fun invoke(model: CreateRequestModel): Either<CreateRequestError, AuthorizationRequest> = either {
        val requestedByParty =
            partyService
                .resolve(model.meta.requestedBy)
                .mapLeft { CreateRequestError.RequestedByPartyError }
                .bind()

        ensure(model.authorizedParty == requestedByParty) {
            CreateRequestError.AuthorizationError
        }

        val requestedFromParty =
            partyService
                .resolve(model.meta.requestedFrom)
                .mapLeft { CreateRequestError.RequestedFromPartyError }
                .bind()

        val requestedToParty =
            partyService
                .resolve(model.meta.requestedTo)
                .mapLeft { CreateRequestError.RequestedByPartyError }
                .bind()

        val businessCommand =
            proxyRequestBusinessHandler
                .validateAndReturnRequestCommand(model)
                .mapLeft { validationError -> CreateRequestError.ValidationError(validationError) }
                .bind()

        val metaAttributes = businessCommand.meta.toMetaAttributes()

        val requestToCreate =
            AuthorizationRequest.create(
                type = businessCommand.type,
                requestedFrom = requestedFromParty,
                requestedBy = requestedByParty,
                requestedTo = requestedToParty,
                validTo = businessCommand.validTo,
            )

        val result = transaction {
            val savedRequest =
                requestRepo
                    .insert(requestToCreate)
                    .mapLeft { CreateRequestError.PersistenceError }
                    .bind()

            val requestProperties: List<AuthorizationRequestProperty> = metaAttributes.map {
                AuthorizationRequestProperty(
                    requestId = savedRequest.id,
                    key = it.key,
                    value = it.value,
                )
            }

            requestPropertyRepo
                .insert(requestProperties)
                .mapLeft { CreateRequestError.PersistenceError }
                .bind()

            savedRequest.copy(properties = requestProperties)
        }

        result
    }
}

sealed class CreateRequestError {
    data object MappingError : CreateRequestError()

    data object AuthorizationError : CreateRequestError()

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
    fun validateAndReturnRequestCommand(createRequestModel: CreateRequestModel): Either<RequestTypeValidationError, RequestCommand>
    fun getCreateGrantProperties(request: AuthorizationRequest): CreateGrantProperties
}
