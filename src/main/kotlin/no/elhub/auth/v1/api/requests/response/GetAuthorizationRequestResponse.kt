package no.elhub.auth.v1.api.requests.response

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.v1.api.common.response.PartyTypeResponse
import no.elhub.auth.v1.api.common.response.RequestedScopeResponse
import no.elhub.auth.v1.api.common.response.ResponseGrantRelationship
import no.elhub.auth.v1.api.common.response.ResponseRelationship
import no.elhub.auth.v1.api.common.response.toApprovalRelationship
import no.elhub.auth.v1.api.common.response.toResponse
import no.elhub.auth.v1.api.common.response.toResponseIdentifier
import no.elhub.auth.v1.api.common.response.toResponseRelationship
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import kotlin.time.Instant

private const val REQUESTS_PATH = "/access/v1/authorization-requests"

typealias JsonApiGetAuthorizationRequestResponse = JsonApiResponse.Document<AuthorizationRequestResource>

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
    val requestedScopes: List<RequestedScopeResponse>,
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
    val requestedBy: ResponseRelationship<PartyTypeResponse>,
    val requestedPrincipal: ResponseRelationship<PartyTypeResponse>,
    val requestedOf: ResponseRelationship<PartyTypeResponse>,
    val approvedBy: ResponseRelationship<PartyTypeResponse>? = null,
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
        links = JsonApiLinks.ResourceObjectLink(self = "$REQUESTS_PATH/$id"),
        meta = buildJsonObject {
            put("createdAt", createdAt.toString())
        },
    )
