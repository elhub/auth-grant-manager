package no.elhub.auth.features.documents.common

import kotlinx.serialization.json.JsonElement

data class AuthorizationDocumentProperty(
    val key: String,
    val value: JsonElement,
)
