package no.elhub.auth.features.grants

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import no.elhub.auth.model.AuthorizationGrant
import no.elhub.auth.model.GrantStatus
import no.elhub.auth.model.RelationshipLink

@Serializable
data class AuthorizationGrantData(
    val id: String,
    val type: String = "AuthorizationGrant",
    val attributes: Attributes,
    val relationships: Relationships,
) {

    @Serializable
    data class Attributes(
        val status: GrantStatus,
        val grantedAt: LocalDateTime,
        val validFrom: LocalDateTime,
        val validTo: LocalDateTime
    )

    @Serializable
    data class Relationships(
        val grantedFor: RelationshipLink,
        val grantedBy: RelationshipLink,
        val grantedTo: RelationshipLink
    )

    companion object {
        fun from(grant: AuthorizationGrant): AuthorizationGrantData = AuthorizationGrantData(
            id = grant.id,
            attributes = Attributes(
                status = grant.grantStatus,
                grantedAt = grant.grantedAt,
                validFrom = grant.validFrom,
                validTo = grant.validTo
            ),
            relationships = Relationships(
                grantedFor = RelationshipLink(
                    data = RelationshipLink.DataLink(
                        id = grant.grantedFor,
                        type = "Person"
                    )
                ),
                grantedBy = RelationshipLink(
                    data = RelationshipLink.DataLink(
                        id = grant.grantedBy,
                        type = "Person"
                    )
                ),
                grantedTo = RelationshipLink(
                    data = RelationshipLink.DataLink(
                        id = grant.grantedTo,
                        type = "Organization"
                    )
                )
            )
        )
    }
}
