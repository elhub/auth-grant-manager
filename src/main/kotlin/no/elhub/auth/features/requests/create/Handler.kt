package no.elhub.auth.features.requests.create

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.common.toAuthorizationParty
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestProperty
import no.elhub.auth.features.requests.common.RequestPropertiesRepository
import no.elhub.auth.features.requests.common.RequestRepository
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.command.toAuthorizationRequestType
import java.util.UUID

class Handler(
    private val requestRepo: RequestRepository,
    private val requestPropertyRepo: RequestPropertiesRepository
) {
    suspend operator fun invoke(command: RequestCommand): Either<CreateRequestError, AuthorizationRequest> {
        val requestedFromParty = command.requestedFrom.toAuthorizationParty()
            .getOrElse { return CreateRequestError.RequestedFromPartyError.left() }

        val requestedByParty = command.requestedBy.toAuthorizationParty()
            .getOrElse { return CreateRequestError.RequestedByPartyError.left() }

        val requestType = command.toAuthorizationRequestType()

        val requestToCreate = AuthorizationRequest.create(
            type = requestType,
            requestedFrom = requestedFromParty,
            requestedBy = requestedByParty,
        )

        val savedRequest = requestRepo.insert(requestToCreate)
            .getOrElse { return CreateRequestError.PersistenceError.left() }

        val requestProperties = command.meta
            .toMetaAttributes()
            .toRequestProperties(savedRequest.id)

        requestPropertyRepo.insert(requestProperties)

        return savedRequest.right()
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
