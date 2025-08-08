package no.elhub.auth.presentation.jsonapi

import kotlinx.serialization.Serializable

@Serializable
data class ResponseLink(
    val self: String
)
