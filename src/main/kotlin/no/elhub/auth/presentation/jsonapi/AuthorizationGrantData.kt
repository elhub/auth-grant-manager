package no.elhub.auth.presentation.jsonapi

import kotlinx.serialization.Serializable
import no.elhub.auth.domain.grant.AuthorizationGrant
import no.elhub.auth.domain.shared.AuthorizationParty
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

@Serializable
data class GrantResponseAttributes(
    val status: String,
    val grantedAt: String,
    val validFrom: String,
    val validTo: String,
) : JsonApiAttributes

@Serializable
data class GrantRelationships(
    val grantedFor: JsonApiRelationshipToOne,
    val grantedBy: JsonApiRelationshipToOne,
    val grantedTo: JsonApiRelationshipToOne,
) : JsonApiRelationships

@Serializable
data class AuthorizationGrantsResponse(
    val data: List<JsonApiResponseResourceObjectWithRelationships<GrantResponseAttributes, GrantRelationships>>,
)

@Serializable
data class AuthorizationGrantResponse(
    val data: JsonApiResponseResourceObjectWithRelationships<GrantResponseAttributes, GrantRelationships>,
)

fun AuthorizationGrant.toGetAuthorizationGrantResponse(partyLookup: (Long) -> AuthorizationParty): AuthorizationGrantResponse {
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

fun List<AuthorizationGrant>.toGetAuthorizationGrantsResponse(partyLookup: (Long) -> AuthorizationParty): AuthorizationGrantsResponse =
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
