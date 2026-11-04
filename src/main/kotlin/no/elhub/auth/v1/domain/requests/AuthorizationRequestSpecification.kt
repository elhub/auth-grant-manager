package no.elhub.auth.v1.domain.requests

data class AuthorizationRequestSpecification(
    val externalReference: String?,
    val redirectUri: String?,
    val details: AuthorizationRequestTypeDetails,
)
