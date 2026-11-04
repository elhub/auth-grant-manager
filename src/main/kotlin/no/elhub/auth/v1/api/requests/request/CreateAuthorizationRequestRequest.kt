package no.elhub.auth.v1.api.requests.request

import kotlinx.serialization.Serializable
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import kotlin.time.Instant

@Serializable
data class CreateAuthorizationRequestAttributes(
    val requestType: AuthorizationRequestType,
    val requestedScopes: List<RequestedScope>? = null,
    val externalReference: String? = null,
    val requestedGrantValidFrom: Instant? = null,
    val requestedGrantValidTo: Instant? = null,
    val redirectUri: String? = null,
) : JsonApiAttributes

@Serializable
data class RequestedScope(
    val resourceType: String,
    val appliesTo: List<ScopeConstraint>,
    val allowedChanges: List<ScopeConstraint> = emptyList(),
)

@Serializable
data class ScopeConstraint(
    val attribute: String,
    val value: List<String>,
)

@Serializable
data class CreateAuthorizationRequestMeta(
    val requestedPrincipal: String,
    val requestedOf: String,
)

@Serializable
data class CreateAuthorizationRequestData(
    val type: String,
    val attributes: CreateAuthorizationRequestAttributes,
    val meta: CreateAuthorizationRequestMeta,
)

@Serializable
data class JsonApiCreateAuthorizationRequestRequest(
    val data: CreateAuthorizationRequestData,
)
