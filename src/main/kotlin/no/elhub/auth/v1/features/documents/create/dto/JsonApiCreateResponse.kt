package no.elhub.auth.v1.features.documents.create.dto

import kotlinx.serialization.Serializable
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.domain.ResourceType
import kotlin.time.Instant
import no.elhub.auth.v1.domain.ResourceConstraint as DomainResourceConstraint

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
    val requestedScopes: List<ResponseRequestedScope>,
    val externalReference: String? = null,
    val status: AuthorizationDocumentStatus,
    val createdAt: Instant,
    val validTo: Instant,
)

@Serializable
data class ResponseRequestedScope(
    val resourceType: ResourceType,
    val appliesTo: List<ResponseScopeConstraint>,
    val allowedChanges: List<ResponseScopeConstraint>,
)

@Serializable
data class ResponseScopeConstraint(
    val attribute: String,
    val value: List<String>,
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
                validTo = validTo,
            ),
            links = AuthorizationDocumentLinks(
                self = "$DOCUMENTS_PATH/$id",
                file = "$DOCUMENTS_PATH/$id.pdf",
            ),
        ),
    )

private fun RequestedAuthorizationScope.toResponse() = ResponseRequestedScope(
    resourceType = resourceType,
    appliesTo = constraints
        .filter { it.constraintKind == AuthorizationScopeConstraintKind.AppliesTo }
        .map(AuthorizationScopeConstraint::toResponse),
    allowedChanges = constraints
        .filter { it.constraintKind == AuthorizationScopeConstraintKind.AllowedChanges }
        .map(AuthorizationScopeConstraint::toResponse),
)

private fun AuthorizationScopeConstraint.toResponse() = ResponseScopeConstraint(
    attribute = attribute.apiName,
    value = when (val constraint = value) {
        is DomainResourceConstraint.MeteringPoints -> constraint.ids.map { it.value }.sorted()
        is DomainResourceConstraint.ContractTypes -> constraint.types.map { it.name }.sorted()
        is DomainResourceConstraint.ValidFrom -> listOf(constraint.value.toString())
    },
)
