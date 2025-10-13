package no.elhub.auth.features.documents.common

import kotlinx.serialization.Serializable

@Serializable
data class PartyRef(
    val type: String, // TODO ElhubResourceType?
    val id: String
)
