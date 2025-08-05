package no.elhub.auth.model

import java.util.UUID

object AuthorizationExceptions {
    class NotFoundException(
        val id: UUID,
        override val message: String = "Not found for ID: $id"
    ) : RuntimeException(message)

    class MissingIdException(
        override val message: String = "Missing id. "
    ): Exception(message)

    class MalformedIdException(
        override val message: String = "Malformed id. "
    ): Exception(message)

    class InvalidRequestTypeException(
        override val message: String = "Invalid requestType "
    ): Exception(message)

}
