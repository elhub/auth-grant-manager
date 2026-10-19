package no.elhub.auth.v1.api.common.errors

import no.elhub.auth.v1.Errors

sealed class InputError(override val detail: String) : Errors(detail) {
    class MissingInputError(detail: String) : InputError(detail)
    class MalformedInputError(detail: String) : InputError(detail)
    class IdMismatchError(detail: String) : InputError(detail)
    class MissingFieldError(val fields: List<String>) : InputError(
        "Missing fields: $fields"
    )

    class InvalidFieldValueError(detail: String) : InputError(detail)
    class ContentTooLargeError(detail: String) : InputError(detail)
    class InvalidCreateAuthorizationDocumentPayload(
        detail: String,
    ) : InputError(detail)

    class RequestedOfRequestedPrincipalMismatch : Errors()
    class RequestedScopeNotAllowed : Errors()
}
