package no.elhub.auth.v1.api.documents.request

import kotlinx.serialization.Serializable
import no.elhub.auth.v1.common.OffsetDateTimeSerializer
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import java.time.OffsetDateTime

@Serializable
data class CreateAuthorizationDocumentAttributes(
    val documentType: AuthorizationDocumentType,
    val requestedScopes: List<RequestedScope>? = null,
    val externalReference: String? = null,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val requestedGrantValidFrom: OffsetDateTime? = null,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val requestedGrantValidTo: OffsetDateTime? = null,
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
data class CreateAuthorizationDocumentMeta(
    val requestedPrincipal: String,
    val requestedOf: String,
    val language: DocumentLanguage = DocumentLanguage.NB,
)

@Serializable
data class CreateAuthorizationDocumentData(
    val type: String,
    val attributes: CreateAuthorizationDocumentAttributes,
    val meta: CreateAuthorizationDocumentMeta,
)

@Serializable
data class JsonApiCreateAuthorizationDocumentRequest(
    val data: CreateAuthorizationDocumentData,
)
