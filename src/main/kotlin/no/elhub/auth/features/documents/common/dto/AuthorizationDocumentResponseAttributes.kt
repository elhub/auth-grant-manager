package no.elhub.auth.features.documents.common.dto

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiAttributes

@Serializable
data class AuthorizationDocumentResponseAttributes(
    val status: String,
    val documentType: String,
    val validTo: String,
    val createdAt: String,
    val updatedAt: String,
) : JsonApiAttributes
