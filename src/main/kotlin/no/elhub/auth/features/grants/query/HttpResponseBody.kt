package no.elhub.auth.features.grants.query

import kotlinx.serialization.Serializable
import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.auth.features.grants.AuthorizationParty
import no.elhub.auth.features.grants.common.GrantRelationships
import no.elhub.auth.features.grants.common.GrantResponseAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

@Serializable
data class AuthorizationGrantsResponse(
    val data: List<JsonApiResponseResourceObjectWithRelationships<GrantResponseAttributes, GrantRelationships>>,
)

fun List<AuthorizationGrant>.toResponseBody(partyLookup: (Long) -> AuthorizationParty): AuthorizationGrantsResponse =
    AuthorizationGrantsResponse(
        data = this.map { authorizationGrant ->
            val attributes = GrantResponseAttributes(
                status = authorizationGrant.grantStatus.toString(),
                grantedAt = authorizationGrant.grantedAt.toString(),
                validFrom = authorizationGrant.validFrom.toString(),
                validTo = authorizationGrant.validTo.toString()
            )

            val relationships = GrantRelationships(
                grantedFor = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = authorizationGrant.grantedFor.toString(),
                        type = partyLookup(authorizationGrant.grantedFor).type.name
                    )
                ),
                grantedBy = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = authorizationGrant.grantedBy.toString(),
                        type = partyLookup(authorizationGrant.grantedBy).type.name
                    )
                ),
                grantedTo = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = authorizationGrant.grantedTo.toString(),
                        type = partyLookup(authorizationGrant.grantedTo).type.name
                    )
                )
            )

            JsonApiResponseResourceObjectWithRelationships(
                type = "AuthorizationGrant",
                id = authorizationGrant.id,
                attributes = attributes,
                relationships = relationships
            )
        }
    )

