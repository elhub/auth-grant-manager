package no.elhub.auth.v1

sealed class Errors(
    message: String? = null,
) : RuntimeException(message) {
    class InvalidMeteringPointId(
        value: String,
    ) : Errors("Invalid metering-point ID: '$value'. Expected exactly 18 digits")

    class InvalidCreateAuthorizationDocumentPayload(
        val detail: String,
    ) : Errors(detail)

    data object RequestedToRequestedFromMismatch : Errors()
    data object RequestedScopeNotAllowed : Errors()
}
