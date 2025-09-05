package no.elhub.auth.features.requests.create

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.RequestRepository
import java.util.UUID

class CreateRequestHandler(private val repo: RequestRepository) {
    operator fun invoke(command: CreateRequestCommand): Either<CreateRequestError, UUID> =
        repo.insert(command.type, command.requester, command.requestee)
            .getOrElse { return CreateRequestError.PersistenceError.left() }
            .right()
}

sealed class CreateRequestError {
    data object MappingError : CreateRequestError()
    data object PersistenceError : CreateRequestError()
}
