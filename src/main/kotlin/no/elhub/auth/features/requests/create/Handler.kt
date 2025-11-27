package no.elhub.auth.features.requests.create

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.common.PartyService
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestProperty
import no.elhub.auth.features.requests.common.RequestPropertiesRepository
import no.elhub.auth.features.requests.common.RequestRepository
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.command.toAuthorizationRequestType
import java.time.LocalDate
import java.util.UUID

class Handler(
    private val requestRepo: RequestRepository,
    private val requestPropertyRepo: RequestPropertiesRepository,
    private val partyService: PartyService
) {
    suspend operator fun invoke(command: RequestCommand): Either<CreateRequestError, AuthorizationRequest> {
        val requestedFromParty = partyService.resolve(command.requestedFrom)
            .getOrElse { return CreateRequestError.RequestedFromPartyError.left() }

        val requestedByParty = partyService.resolve(command.requestedBy)
            .getOrElse { return CreateRequestError.RequestedByPartyError.left() }

        val requestedToParty = partyService.resolve(command.requestedTo)
            .getOrElse { return CreateRequestError.RequestedByPartyError.left() }

        val requestType = command.toAuthorizationRequestType()

        val validTo = LocalDate.parse(command.validTo)

        val requestToCreate = AuthorizationRequest.create(
            type = requestType,
            requestedFrom = requestedFromParty,
            requestedBy = requestedByParty,
            requestedTo = requestedToParty,
            validTo = validTo
        )

        val savedRequest = requestRepo.insert(requestToCreate).getOrElse { return CreateRequestError.PersistenceError.left() }

        val metaAttributes = command.meta.toMetaAttributes()

        val requestProperties = metaAttributes.toRequestProperties(savedRequest.id)

        requestPropertyRepo.insert(requestProperties)

        val requestToRespond = AuthorizationRequest.create(
            type = requestType,
            requestedFrom = requestedFromParty,
            requestedBy = requestedByParty,
            requestedTo = requestedToParty,
            validTo = validTo,
            properties = metaAttributes
        )

        return requestToRespond.right()
    }
}

sealed class CreateRequestError {
    data object MappingError : CreateRequestError()
    data object PersistenceError : CreateRequestError()
    data object RequestedFromPartyError : CreateRequestError()
    data object RequestedByPartyError : CreateRequestError()
}

fun Map<String, String>.toRequestProperties(requestId: UUID) =
    this.map { (key, value) ->
        AuthorizationRequestProperty(
            requestId = requestId,
            key = key,
            value = value
        )
    }.toList()
