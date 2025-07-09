package no.elhub.auth.grantmanager.presentation.features.grants

import kotlinx.serialization.Serializable
import no.elhub.auth.grantmanager.presentation.model.AuthorizationGrant
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.response.JsonApiResponse
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

typealias AuthorizationGrantResponse = JsonApiResponse.SingleDocumentWithRelationships<GrantResponseAttributes, GrantRelationships>
typealias AuthorizationGrantsResponse = JsonApiResponse.CollectionDocumentWithRelationships<GrantResponseAttributes, GrantRelationships>

fun AuthorizationGrant.toGetAuthorizationGrantResponse(): AuthorizationGrantResponse {
    val attributes = GrantResponseAttributes(
        status = this.grantStatus.toString(),
        grantedAt = this.grantedAt.toString(),
        validFrom = this.validFrom.toString(),
        validTo = this.validTo.toString()
    )

    val relationships = GrantRelationships(
        grantedFor = JsonApiRelationshipToOne(
            data = JsonApiRelationshipData(
                id = this.grantedFor,
                type = "Person"
            )
        ),
        grantedBy = JsonApiRelationshipToOne(
            data = JsonApiRelationshipData(
                id = this.grantedBy,
                type = "Person"
            )
        ),
        grantedTo = JsonApiRelationshipToOne(
            data = JsonApiRelationshipData(
                id = this.grantedTo,
                type = "Organization"
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

fun List<AuthorizationGrant>.toGetAuthorizationGrantsResponse(): AuthorizationGrantsResponse = AuthorizationGrantsResponse(
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
                    id = authorizationGrant.grantedFor,
                    type = "Person"
                )
            ),
            grantedBy = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    id = authorizationGrant.grantedBy,
                    type = "Person"
                )
            ),
            grantedTo = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    id = authorizationGrant.grantedTo,
                    type = "Organization"
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
