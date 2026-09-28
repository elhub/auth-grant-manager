package no.elhub.auth.v1.features.documents.create.dto

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.DocumentLanguage
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
    val meta: AuthorizationDocumentMeta,
    val links: AuthorizationDocumentLinks,
)

@Serializable
data class AuthorizationDocumentAttributes(
    val documentType: AuthorizationDocumentType,
    val requestedScope: ResponseRequestedScope,
    val externalReference: String? = null,
    val status: AuthorizationDocumentStatus,
    val createdAt: Instant,
    val validTo: Instant? = null,
)

@Serializable
data class ResponseRequestedScope(
    val appliesTo: ResponseResourceConstraint,
)

@Serializable
data class ResponseResourceConstraint(
    val meteringPointIds: Set<String>,
)

@Serializable
data class AuthorizationDocumentMeta(
    val requestedFrom: String,
    val requestedTo: String,
    val language: DocumentLanguage,
)

@Serializable
data class AuthorizationDocumentLinks(
    val self: String,
    val file: String,
)

fun AuthorizationDocument.toCreateResponse(language: DocumentLanguage) =
    JsonApiCreateAuthorizationDocumentResponse(
        data = AuthorizationDocumentResource(
            id = id,
            type = "AuthorizationDocument",
            attributes = AuthorizationDocumentAttributes(
                documentType = documentType,
                requestedScope = ResponseRequestedScope(
                    appliesTo = ResponseResourceConstraint(
                        meteringPointIds = resourceConstraints
                            .filterIsInstance<DomainResourceConstraint.MeteringPoints>()
                            .flatMap { constraint -> constraint.ids.map { it.value } }
                            .toSet(),
                    ),
                ),
                externalReference = externalReference,
                status = status,
                createdAt = createdAt,
                validTo = validTo,
            ),
            meta = AuthorizationDocumentMeta(
                requestedFrom = requestedFrom.id,
                requestedTo = requestedTo.id,
                language = language,
            ),
            links = AuthorizationDocumentLinks(
                self = "$DOCUMENTS_PATH/$id",
                file = "$DOCUMENTS_PATH/$id.pdf",
            ),
        ),
    )
