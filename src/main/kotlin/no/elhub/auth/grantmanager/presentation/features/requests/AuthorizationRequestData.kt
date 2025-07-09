package no.elhub.auth.grantmanager.presentation.features.requests

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import no.elhub.auth.grantmanager.presentation.model.AuthorizationRequest
import no.elhub.auth.grantmanager.presentation.model.RelationshipLink
import no.elhub.auth.grantmanager.presentation.model.RequestStatus

/**
 * Data class for the response object for AuthorizationRequest.
 */
@Serializable
data class AuthorizationRequestData(
    val id: String,
    val type: String,
    val attributes: Attributes,
    val relationships: Relationships,
    val meta: Meta,
) {

    @Serializable
    data class Attributes(
        val status: RequestStatus,
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime,
        val validTo: LocalDateTime,
    )

    @Serializable
    data class Relationships(
        val requestedBy: RelationshipLink,
        val requestedTo: RelationshipLink,
    )

    @Serializable
    data class Meta(
        val contract: String?
    )

    companion object {
        fun from(request: AuthorizationRequest): AuthorizationRequestData = AuthorizationRequestData(
            id = request.id,
            type = "AuthorizationRequest",
            attributes = Attributes(
                status = request.requestStatus,
                createdAt = request.createdAt,
                updatedAt = request.updatedAt,
                validTo = request.validTo,
            ),
            relationships = Relationships(
                requestedBy = RelationshipLink(
                    data = RelationshipLink.DataLink(
                        id = request.requestedBy,
                        type = "User"
                    )
                ),
                requestedTo = RelationshipLink(
                    data = RelationshipLink.DataLink(
                        id = request.requestedTo,
                        type = "User"
                    )
                )
            ),
            meta = Meta(
                contract = request.properties.find { it.key == "contract" }?.value
            )
        )
    }
}
