package no.elhub.auth.model

import kotlinx.serialization.Serializable

@Serializable
data class RelationshipLink(
    val data: DataLink
) {

    @Serializable
    data class DataLink(
        val id: String,
        val type: String,
    )
}
