package no.elhub.auth.features.grants

import kotlinx.serialization.Serializable
import no.elhub.auth.model.AuthorizationGrant
import no.elhub.auth.model.AuthorizationParty
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObject
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
data class AuthorizationGrantsResponseNew(
    val data: List<JsonApiResponseResourceObjectWithRelationships<GrantResponseAttributes, GrantRelationships>>,
    val included: List<JsonApiResponseResourceObject<PartyAttributes>>
)

@Serializable
data class AuthorizationGrantResponseNew(
    val data: JsonApiResponseResourceObjectWithRelationships<GrantResponseAttributes, GrantRelationships>,
    val included: List<JsonApiResponseResourceObject<PartyAttributes>>
)

@Serializable
data class PartyAttributes(
    val partyType: String,
    val descriptor: String,
    val name: String?,
    val createdAt: String
) : JsonApiAttributes

const val AUTHORIZATION_PARTY = "authorizationParty" // resource type -> refer to json:api spec v1.1

fun buildIncludedPartiesForGrants(grants: List<AuthorizationGrant>, partyLookup: (Long) -> AuthorizationParty?): List<JsonApiResponseResourceObject<PartyAttributes>> {
    val partyIds = grants.flatMap { listOf(it.grantedFor, it.grantedBy, it.grantedTo) }.toSet()
    return partyIds.mapNotNull { id ->
        partyLookup(id)?.let { party ->
            JsonApiResponseResourceObject(
                id = id.toString(),
                type = AUTHORIZATION_PARTY,
                attributes = PartyAttributes(
                    partyType = party.type.name,
                    descriptor = party.descriptor,
                    name = party.name,
                    createdAt = party.createdAt.toString()
                )
            )
        }
    }
}

fun buildIncludedPartiesForGrant(grant: AuthorizationGrant, partyLookup: (Long) -> AuthorizationParty?): List<JsonApiResponseResourceObject<PartyAttributes>> {
    val partyIds = listOf(grant.grantedFor, grant.grantedBy, grant.grantedTo).toSet()
    return partyIds.mapNotNull { id ->
        partyLookup(id)?.let { party ->
            JsonApiResponseResourceObject(
                id = id.toString(),
                type = AUTHORIZATION_PARTY,
                attributes = PartyAttributes(
                    partyType = party.type.name,
                    descriptor = party.descriptor,
                    name = party.name,
                    createdAt = party.createdAt.toString()
                )
            )
        }
    }
}

fun AuthorizationGrant.toGetAuthorizationGrantResponse(partyLookup: (Long) -> AuthorizationParty): AuthorizationGrantResponseNew {
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
                type = AUTHORIZATION_PARTY
            )
        ),
        grantedBy = JsonApiRelationshipToOne(
            data = JsonApiRelationshipData(
                id = this.grantedBy.toString(),
                type = AUTHORIZATION_PARTY
            )
        ),
        grantedTo = JsonApiRelationshipToOne(
            data = JsonApiRelationshipData(
                id = this.grantedTo.toString(),
                type = AUTHORIZATION_PARTY
            )
        )
    )

    return AuthorizationGrantResponseNew(
        data = JsonApiResponseResourceObjectWithRelationships(
            type = "AuthorizationGrant",
            id = this.id,
            attributes = attributes,
            relationships = relationships,
        ),
        included = buildIncludedPartiesForGrant(this, partyLookup)
    )
}

fun List<AuthorizationGrant>.toGetAuthorizationGrantsResponse(partyLookup: (Long) -> AuthorizationParty): AuthorizationGrantsResponseNew =
    AuthorizationGrantsResponseNew(
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
                        type = AUTHORIZATION_PARTY,
                    )
                ),
                grantedBy = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = authorizationGrant.grantedBy.toString(),
                        type = AUTHORIZATION_PARTY
                    )
                ),
                grantedTo = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = authorizationGrant.grantedTo.toString(),
                        type = AUTHORIZATION_PARTY
                    )
                )
            )

            JsonApiResponseResourceObjectWithRelationships(
                type = "AuthorizationGrant",
                id = authorizationGrant.id,
                attributes = attributes,
                relationships = relationships
            )
        },
        included = buildIncludedPartiesForGrants(this, partyLookup)
    )
