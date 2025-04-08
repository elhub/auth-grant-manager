package no.elhub.auth.features.documents.responses

import kotlinx.serialization.Serializable
import no.elhub.auth.model.ResponseMeta

@Serializable
data class PostAuthorizationDocumentResponse(
    val id: String,
    val meta: ResponseMeta
)
