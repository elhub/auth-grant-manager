package no.elhub.auth.v1.features.documents.get.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.features.common.dto.ResponseGrantRelationship
import no.elhub.auth.v1.features.common.dto.ResponseRelationship
import no.elhub.auth.v1.features.common.dto.PartyTypeResponse
import no.elhub.auth.v1.features.common.dto.RequestedScopeResponse
import no.elhub.auth.v1.features.common.dto.toApprovalRelationship
import no.elhub.auth.v1.features.common.dto.toResponse
import no.elhub.auth.v1.features.common.dto.toResponseIdentifier
import no.elhub.auth.v1.features.common.dto.toResponseRelationship
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import kotlin.time.Instant

private const val DOCUMENTS_PATH = "/access/v1/authorization-documents"

typealias JsonApiGetAuthorizationDocumentResponse = JsonApiResponse.Document<AuthorizationDocumentResource>

@Serializable
data class AuthorizationDocumentResource(
    val id: String,
    val type: String,
    val attributes: AuthorizationDocumentAttributes,
    val relationships: AuthorizationDocumentRelationships,
    val links: AuthorizationDocumentLinks,
)

@Serializable
data class AuthorizationDocumentAttributes(
    val documentType: AuthorizationDocumentType,
    val requestedScopes: List<RequestedScopeResponse>,
    val externalReference: String? = null,
    val documentUri: String,
    val status: ResponseAuthorizationDocumentStatus,
    val validFrom: Instant,
    val validTo: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
enum class ResponseAuthorizationDocumentStatus {
    Signed,
    Expired,
    Pending,
}

@Serializable
data class AuthorizationDocumentRelationships(
    val requestedBy: ResponseRelationship<PartyTypeResponse>,
    val requestedPrincipal: ResponseRelationship<PartyTypeResponse>,
    val requestedOf: ResponseRelationship<PartyTypeResponse>,
    val approvedBy: ResponseRelationship<PartyTypeResponse>? = null,
    val authorizationGrant: ResponseGrantRelationship? = null,
)

@Serializable
data class AuthorizationDocumentLinks(
    val self: String,
    val file: String,
)

fun AuthorizationDocument.toGetResponse() =
    JsonApiGetAuthorizationDocumentResponse(
        data = AuthorizationDocumentResource(
            id = id,
            type = "AuthorizationDocument",
            attributes = AuthorizationDocumentAttributes(
                documentType = documentType,
                requestedScopes = requestedScopes.map(RequestedAuthorizationScope::toResponse),
                externalReference = externalReference,
                documentUri = "$DOCUMENTS_PATH/$id.pdf",
                status = when (status) {
                    AuthorizationDocumentStatus.Accepted -> ResponseAuthorizationDocumentStatus.Signed
                    AuthorizationDocumentStatus.Expired -> ResponseAuthorizationDocumentStatus.Expired
                    AuthorizationDocumentStatus.Pending -> ResponseAuthorizationDocumentStatus.Pending
                },
                validFrom = validFrom,
                validTo = validTo,
                createdAt = createdAt,
                updatedAt = updatedAt,
            ),
            relationships = AuthorizationDocumentRelationships(
                requestedBy = requestedBy.toResponseRelationship(),
                requestedPrincipal = requestedPrincipal.toResponseRelationship(),
                requestedOf = requestedOf.toResponseRelationship(),
                approvedBy = approvedBy?.let { it.toApprovalRelationship() },
                authorizationGrant = authorizationGrant?.let {
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
