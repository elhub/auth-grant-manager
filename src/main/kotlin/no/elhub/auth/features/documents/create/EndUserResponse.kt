package no.elhub.auth.features.documents.create

import kotlinx.serialization.Serializable

@Serializable
data class EndUserApiResponseBody(
    val data: EndUserData
)

@Serializable
data class EndUserData(
    val type: String,
    val id: String
)
