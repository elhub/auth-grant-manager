package no.elhub.auth.features.documents.common

import java.util.UUID

data class AuthorizationDocumentProperty(
    val documentId: UUID,
    val key: String,
    val value: String
)
