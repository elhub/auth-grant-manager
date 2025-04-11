package no.elhub.auth.features.documents.jsonApiSpec

import kotlinx.serialization.Serializable

class PostAuthorizationDocument {
    @Serializable
    data class Request(
        val data: Data
    ) {
        @Serializable
        data class Data(
            val type: String,
            val attributes: Attributes
        ) {
            @Serializable
            data class Attributes(
                val requestedBy: String,
                val requestedTo: String
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
            val attributes: Attributes
        ) {
            @Serializable
            data class Attributes(
                val status: String,
                val requestedBy: String,
                val requestedTo: String,
                val createdAt: String,
                val updatedAt: String
            )
        }
    }
}
