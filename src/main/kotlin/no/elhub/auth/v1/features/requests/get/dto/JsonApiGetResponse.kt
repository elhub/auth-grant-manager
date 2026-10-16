package no.elhub.auth.v1.features.requests.get.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.features.common.dto.ResponseGrantRelationship
import no.elhub.auth.v1.features.common.dto.ResponseRelationship
import no.elhub.auth.v1.features.common.dto.ResponseRequestedScope
import no.elhub.auth.v1.features.common.dto.toApprovalRelationship
import no.elhub.auth.v1.features.common.dto.toResponse
import no.elhub.auth.v1.features.common.dto.toResponseIdentifier
import no.elhub.auth.v1.features.common.dto.toResponseRelationship
import kotlin.time.Instant

private const val REQUESTS_PATH = "/access/v1/authorization-requests"

@Serializable
data class JsonApiGetAuthorizationRequestResponse(
    val data: AuthorizationRequestResource,
)

@Serializable
data class AuthorizationRequestResource(
    val id: String,
    val type: String,
    val attributes: AuthorizationRequestAttributes,
    val meta: JsonObject,
    val relationships: AuthorizationRequestRelationships,
    val links: AuthorizationRequestLinks,
)

@Serializable
data class AuthorizationRequestAttributes(
    val requestType: AuthorizationRequestType,
    val requestedScopes: List<ResponseRequestedScope>,
    val externalReference: String? = null,
    val redirectUri: String? = null,
    val status: AuthorizationRequestStatus,
    val validFrom: Instant,
    val validTo: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
data class AuthorizationRequestRelationships(
    val requestedBy: ResponseRelationship,
    val requestedPrincipal: ResponseRelationship,
    val requestedOf: ResponseRelationship,
    val approvedBy: ResponseRelationship? = null,
    val authorizationGrant: ResponseGrantRelationship? = null,
)

@Serializable
data class AuthorizationRequestLinks(
    val self: String,
)

fun AuthorizationRequest.toGetResponse() =
    JsonApiGetAuthorizationRequestResponse(
        data = AuthorizationRequestResource(
            id = id.toString(),
            type = "AuthorizationRequest",
            attributes = AuthorizationRequestAttributes(
                requestType = requestType,
                requestedScopes = requestedScopes.map(RequestedAuthorizationScope::toResponse),
                externalReference = externalReference,
                redirectUri = redirectUri,
                status = status,
                validFrom = validFrom,
                validTo = validTo,
                createdAt = createdAt,
                updatedAt = updatedAt,
            ),
            meta = meta,
            relationships = AuthorizationRequestRelationships(
                requestedBy = requestedBy.toResponseRelationship(),
                requestedPrincipal = requestedPrincipal.toResponseRelationship(),
                requestedOf = requestedOf.toResponseRelationship(),
                approvedBy = approvedBy?.let { it.toApprovalRelationship() },
                authorizationGrant = authorizationGrant?.let {
                    ResponseGrantRelationship(data = it.map(AuthorizationGrant::toResponseIdentifier))
                },
            ),
            links = AuthorizationRequestLinks(
                self = "$REQUESTS_PATH/$id",
            ),
        ),
    )
