package no.elhub.auth.v1

sealed class Errors(
    open val detail: String? = null,
) : RuntimeException(detail)

sealed class InputError(override val detail: String) : Errors(detail) {
    class MissingInputError(detail: String) : InputError(detail)
    class MalformedInputError(detail: String) : InputError(detail)
    class IdMismatchError(detail: String) : InputError(detail)
    class MissingFieldError(val fields: List<String>) : InputError(
        "Missing fields: $fields"
    )

    class InvalidFieldValueError(detail: String) : InputError(detail)
    class ContentTooLargeError(detail: String) : InputError(detail)
    class InvalidMeteringPointId(
        value: String,
    ) : InputError("Invalid metering-point ID: '$value'. Expected exactly 18 digits")

    class InvalidCreateAuthorizationDocumentPayload(
        detail: String,
    ) : InputError(detail)

    class RequestedToRequestedFromMismatch : Errors()
    class PartiesNotAllowedForDocumentType : Errors()
    class RequestedScopeNotAllowed : Errors()
}

sealed class CommandError(override val detail: String) : Errors(detail) {
    class ResourceNotFoundError(detail: String) : CommandError(detail)
    class IOError(detail: String) : CommandError(detail)
}

sealed class QueryError(override val detail: String) : Errors(detail) {
    class ResourceNotFoundError(detail: String) : QueryError(detail)
    class IOError(detail: String) : QueryError(detail)
    class NotAuthorizedError(detail: String) : QueryError(detail)
}

sealed class RepositoryWriteError(override val detail: String) : Errors(detail) {
    class ConflictError(detail: String) : RepositoryWriteError(detail)
    class NotFoundError(detail: String) : RepositoryWriteError(detail)
    class ExpiredError(detail: String) : RepositoryWriteError(detail)
    class UnexpectedError(detail: String) : RepositoryWriteError(detail)
}

sealed class RepositoryReadError(override val detail: String) : Errors(detail) {
    class NotFoundError(detail: String) : RepositoryReadError(detail)
    class UnexpectedError(detail: String) : RepositoryReadError(detail)
}
