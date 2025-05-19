package no.elhub.auth.features.documents

import kotlinx.serialization.Serializable
import no.elhub.auth.model.RelationshipLink

class PostAuthorizationDocument {
    @Serializable
    data class Request(
        val data: Data
    ) {
        @Serializable
        data class Data(
            val type: String,
            val attributes: Attributes,
            val relationships: Relationships,

        ) {
            @Serializable
            data class Attributes(
                val meteringPoint: String,
            )

            @Serializable
            data class Relationships(
                val requestedBy: RelationshipLink,
                val requestedTo: RelationshipLink,
            )
        }
    }

    @Serializable
    data class Response(
        val data: Data
    ) {
        @Serializable
        data class Data(
            val type: String,
            val id: String,
            val attributes: Attributes,
            val relationships: Relationships
        ) {
            @Serializable
            data class Attributes(
                val status: String,
                val createdAt: String,
                val updatedAt: String
            )

            @Serializable
            data class Relationships(
                val requestedBy: RelationshipLink,
                val requestedTo: RelationshipLink,
            )
        }
    }
}
