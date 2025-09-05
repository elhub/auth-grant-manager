package no.elhub.auth.features.grants.common

import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

typealias AuthorizationGrantResponse = JsonApiResponse.SingleDocumentWithRelationships<GrantResponseAttributes, GrantRelationships>

fun AuthorizationGrant.toResponse() =
    AuthorizationGrantResponse(
        data = JsonApiResponseResourceObjectWithRelationships(
            type = (AuthorizationGrant::class).simpleName ?: "AuthorizationGrant",
            id = this.id,
            attributes = GrantResponseAttributes(
                status = this.grantStatus.toString(),
                grantedAt = this.grantedAt.toString(),
                validFrom = this.validFrom.toString(),
                validTo = this.validTo.toString()
            ),
            relationships = GrantRelationships(
                grantedFor = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = this.grantedFor.id.toString(),
                        type = this.grantedFor.type.name
                    )
                ),
                grantedBy = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = this.grantedBy.id.toString(),
                        type = this.grantedBy.type.name
                    )
                ),
                grantedTo = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = this.grantedTo.id.toString(),
                        type = this.grantedTo.type.name
                    )
                )
            )
        )
    )
