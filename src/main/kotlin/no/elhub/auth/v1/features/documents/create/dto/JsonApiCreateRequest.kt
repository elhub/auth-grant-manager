package no.elhub.auth.v1.features.documents.create.dto

import kotlinx.serialization.Serializable
import no.elhub.auth.v0.features.common.party.PartyIdentifier
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.devxp.jsonapi.model.JsonApiAttributes

@Serializable
data class CreateAuthorizationDocumentAttributes(
    val documentType: AuthorizationDocumentType,
    val requestedScopes: List<RequestedScope>,
    val externalReference: String? = null,
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
    val requestedFrom: PartyIdentifier,
    val requestedTo: PartyIdentifier,
    val language: DocumentLanguage,
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
