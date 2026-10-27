package no.elhub.auth.v1.api.documents.response

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
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import kotlin.time.Instant

private const val DOCUMENTS_PATH = "/access/v1/authorization-documents"

typealias JsonApiGetAuthorizationDocumentResponse = JsonApiResponse.Document<GetAuthorizationDocumentResource>

@Serializable
data class GetAuthorizationDocumentResource(
    val id: String,
    val type: String,
    val attributes: GetAuthorizationDocumentAttributes,
    val meta: JsonObject,
    val relationships: AuthorizationDocumentRelationships,
    val links: AuthorizationDocumentLinks,
)

@Serializable
data class GetAuthorizationDocumentAttributes(
    val documentType: AuthorizationDocumentType,
    val requestedScopes: List<RequestedScopeResponse>,
    val externalReference: String? = null,
    val status: ResponseAuthorizationDocumentStatus,
    val validFrom: Instant,
    val validTo: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val requestedGrantValidFrom: Instant? = null,
    val requestedGrantValidTo: Instant? = null,
)

@Serializable
enum class ResponseAuthorizationDocumentStatus {
    Approved,
    Expired,
    Pending,
}

@Serializable
data class AuthorizationDocumentRelationships(
    val requestedBy: ResponseRelationship<PartyTypeResponse>,
    val requestedPrincipal: ResponseRelationship<PartyTypeResponse>,
    val requestedOf: ResponseRelationship<PartyTypeResponse>,
    val approvedBy: ResponseRelationship<PartyTypeResponse>? = null,
    val authorizationGrants: ResponseGrantRelationship? = null,
)

@Serializable
data class AuthorizationDocumentLinks(
    val self: String,
    val file: String,
)

fun AuthorizationDocument.toGetResponse() =
    JsonApiGetAuthorizationDocumentResponse(
        data = GetAuthorizationDocumentResource(
            id = id,
            type = "AuthorizationDocument",
            attributes = GetAuthorizationDocumentAttributes(
                documentType = documentType,
                requestedScopes = requestedScopes.map(RequestedAuthorizationScope::toResponse),
                externalReference = externalReference,
                status = when (status) {
                    AuthorizationDocumentStatus.Approved -> ResponseAuthorizationDocumentStatus.Approved
                    AuthorizationDocumentStatus.Expired -> ResponseAuthorizationDocumentStatus.Expired
                    AuthorizationDocumentStatus.Pending -> ResponseAuthorizationDocumentStatus.Pending
                },
                validFrom = validFrom,
                validTo = validTo,
                createdAt = createdAt,
                updatedAt = updatedAt,
                requestedGrantValidFrom = requestedGrantValidFrom,
                requestedGrantValidTo = requestedGrantValidTo,
            ),
            meta = meta,
            relationships = AuthorizationDocumentRelationships(
                requestedBy = requestedBy.toResponseRelationship(),
                requestedPrincipal = requestedPrincipal.toResponseRelationship(),
                requestedOf = requestedOf.toResponseRelationship(),
                approvedBy = approvedBy?.let { it.toApprovalRelationship() },
                authorizationGrants = authorizationGrants?.let {
                    ResponseGrantRelationship(data = it.map(AuthorizationGrant::toResponseIdentifier))
                },
            ),
            links = AuthorizationDocumentLinks(
                self = "$DOCUMENTS_PATH/$id",
                file = "$DOCUMENTS_PATH/$id.pdf",
            ),
        ),
        links = JsonApiLinks.ResourceObjectLink(self = "$DOCUMENTS_PATH/$id"),
        meta = buildJsonObject {
            put("createdAt", createdAt.toString())
        },
    )
