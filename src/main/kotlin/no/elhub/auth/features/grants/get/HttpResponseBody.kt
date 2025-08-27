package no.elhub.auth.features.grants.get

import kotlinx.serialization.Serializable
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationParty
import no.elhub.auth.features.grants.common.GrantRelationships
import no.elhub.auth.features.grants.common.GrantResponseAttributes
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

@Serializable
data class AuthorizationGrantResponse(
    val data: JsonApiResponseResourceObjectWithRelationships<GrantResponseAttributes, GrantRelationships>,
)

fun AuthorizationGrant.toResponseBody(partyLookup: (Long) -> AuthorizationParty): AuthorizationGrantResponse {
    val attributes = GrantResponseAttributes(
        status = this.grantStatus.toString(),
        grantedAt = this.grantedAt.toString(),
        validFrom = this.validFrom.toString(),
        validTo = this.validTo.toString()
    )

    val relationships = GrantRelationships(
        grantedFor = JsonApiRelationshipToOne(
            data = JsonApiRelationshipData(
                id = this.grantedFor.toString(),
                type = partyLookup(this.grantedFor).type.name
            )
        ),
        grantedBy = JsonApiRelationshipToOne(
            data = JsonApiRelationshipData(
                id = this.grantedBy.toString(),
                type = partyLookup(this.grantedBy).type.name
            )
        ),
        grantedTo = JsonApiRelationshipToOne(
            data = JsonApiRelationshipData(
                id = this.grantedTo.toString(),
                type = partyLookup(this.grantedTo).type.name
            )
        )
    )

    return AuthorizationGrantResponse(
        data = JsonApiResponseResourceObjectWithRelationships(
            type = "AuthorizationGrant",
            id = this.id,
            attributes = attributes,
            relationships = relationships,
        )
    )
}


