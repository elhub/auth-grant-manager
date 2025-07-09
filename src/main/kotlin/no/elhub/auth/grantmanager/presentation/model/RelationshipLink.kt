package no.elhub.auth.grantmanager.presentation.model

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
