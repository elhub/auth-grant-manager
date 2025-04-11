package no.elhub.auth.features.requests

import kotlinx.serialization.Serializable
import no.elhub.auth.model.RelationshipLink

/**
 * Data class for the request object for AuthorizationRequest.
 */
@Serializable
data class AuthorizationRequestRequest(
    val data: Data,
) {

    @Serializable
    data class Data(
        val type: String,
        val attributes: Attributes,
        val relationships: Relations,
        val meta: Meta,
    )

    @Serializable
    data class Attributes(
        val requestType: String,
    )

    @Serializable
    data class Relations(
        val requestedBy: RelationshipLink,
        val requestedTo: RelationshipLink,
    )

    @Serializable
    data class Meta(
        val contract: String,
    )
}
