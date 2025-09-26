package no.elhub.auth.features.requests.create

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.elhub.auth.features.requests.common.RequestRepository
import java.util.UUID

class Handler(private val repo: RequestRepository) {
    operator fun invoke(command: Command): Either<Error, UUID> =
        repo.insert(command.type, command.requester, command.requestee)
            .getOrElse { return Error.PersistenceError.left() }
            .right()
}

sealed class Error {
    data object MappingError : Error()
    data object PersistenceError : Error()
}
