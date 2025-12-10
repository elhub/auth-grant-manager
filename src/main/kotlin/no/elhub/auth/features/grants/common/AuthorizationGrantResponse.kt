package no.elhub.auth.features.grants.common

import no.elhub.auth.features.grants.AuthorizationGrant
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationships

typealias AuthorizationGrantResponse = JsonApiResponse.SingleDocumentWithRelationships<GrantResponseAttributes, GrantRelationships>

fun AuthorizationGrant.toResponse() =
    AuthorizationGrantResponse(
        data = JsonApiResponseResourceObjectWithRelationships(
            type = (AuthorizationGrant::class).simpleName ?: "AuthorizationGrant",
            id = this.id.toString(),
            attributes = GrantResponseAttributes(
                status = this.grantStatus.toString(),
                grantedAt = this.grantedAt.toString(),
                validFrom = this.validFrom.toString(),
                validTo = this.validTo.toString()
            ),
            relationships = GrantRelationships(
                grantedFor = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = this.grantedFor.resourceId,
                        type = this.grantedFor.type.name
                    )
                ),
                grantedBy = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = this.grantedBy.resourceId,
                        type = this.grantedBy.type.name
                    )
                ),
                grantedTo = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = this.grantedTo.resourceId,
                        type = this.grantedTo.type.name
                    )
                ),
                source = JsonApiRelationshipToOne(
                    data = JsonApiRelationshipData(
                        id = this.sourceId.toString(),
                        type = when (this.sourceType) {
                            AuthorizationGrant.SourceType.Document -> "AuthorizationDocument"
                            AuthorizationGrant.SourceType.Request -> "AuthorizationRequest"
                        }
                    ),
                    links = JsonApiLinks.RelationShipLink(
                        self = when (this.sourceType) {
                            AuthorizationGrant.SourceType.Document -> "/authorization-documents/$sourceId"
                            AuthorizationGrant.SourceType.Request -> "/authorization-requests/$sourceId"
                        }
                    )
                )
            )
        ),
        links = JsonApiLinks.ResourceObjectLink(
            self = "/authorization-grants/${this.id}",
        )
    )
