package no.elhub.auth.features.documents.common.dto

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiResourceLinks

@Serializable
data class AuthorizationDocumentResponseLinks(
    val self: String,
    val file: String,
) : JsonApiResourceLinks
