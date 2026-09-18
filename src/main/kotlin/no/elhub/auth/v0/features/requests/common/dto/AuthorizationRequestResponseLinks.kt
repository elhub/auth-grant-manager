package no.elhub.auth.v0.features.requests.common.dto

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiResourceLinks

@Serializable
data class AuthorizationRequestResponseLinks(
    val self: String,
) : JsonApiResourceLinks
