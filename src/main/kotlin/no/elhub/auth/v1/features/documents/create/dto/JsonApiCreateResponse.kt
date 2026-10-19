package no.elhub.auth.v1.features.documents.create.dto

import kotlinx.serialization.Serializable
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.features.common.dto.RequestedScopeResponse
import no.elhub.auth.v1.features.common.dto.toResponse
import kotlin.time.Instant

private const val DOCUMENTS_PATH = "/access/v1/authorization-documents"

@Serializable
data class JsonApiCreateAuthorizationDocumentResponse(
    val data: AuthorizationDocumentResource,
)

@Serializable
data class AuthorizationDocumentResource(
    val id: String,
    val type: String,
    val attributes: AuthorizationDocumentAttributes,
    val links: AuthorizationDocumentLinks,
)

@Serializable
data class AuthorizationDocumentAttributes(
    val documentType: AuthorizationDocumentType,
    val requestedScopes: List<RequestedScopeResponse>,
    val externalReference: String? = null,
    val status: AuthorizationDocumentStatus,
    val createdAt: Instant,
    val validFrom: Instant,
    val validTo: Instant,
)

@Serializable
data class AuthorizationDocumentLinks(
    val self: String,
    val file: String,
)

fun AuthorizationDocument.toCreateResponse() =
    JsonApiCreateAuthorizationDocumentResponse(
        data = AuthorizationDocumentResource(
            id = id,
            type = "AuthorizationDocument",
            attributes = AuthorizationDocumentAttributes(
                documentType = documentType,
                requestedScopes = requestedScopes.map(RequestedAuthorizationScope::toResponse),
                externalReference = externalReference,
                status = status,
                createdAt = createdAt,
                validFrom = validFrom,
                validTo = validTo,
            ),
            links = AuthorizationDocumentLinks(
                self = "$DOCUMENTS_PATH/$id",
                file = "$DOCUMENTS_PATH/$id.pdf",
            ),
        ),
    )
