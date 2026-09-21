package no.elhub.auth.v1.features.documents.create.dto

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.auth.v0.features.common.party.PartyIdentifier
import no.elhub.devxp.jsonapi.model.JsonApiAttributes

@Serializable
data class CreateAuthorizationDocumentAttributes(
    val documentType: AuthorizationDocumentType,
    val requestedScope: RequestedScope,
    val externalReference: String? = null,
) : JsonApiAttributes

@Serializable
data class RequestedScope(
    val appliesTo: ResourceConstraint,
    val allowedChanges: AllowedChanges? = null,
)

@Serializable
data class ResourceConstraint(
    val meteringPointIds: Set<String>,
)

@Serializable
data class AllowedChanges(
    val validFrom: List<LocalDate> = emptyList(),
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
