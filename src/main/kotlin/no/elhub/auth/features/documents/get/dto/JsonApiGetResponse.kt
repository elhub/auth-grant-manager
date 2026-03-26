package no.elhub.auth.features.documents.get.dto

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.dto.JsonApiResourceMetaMap
import no.elhub.auth.features.common.party.dto.toJsonApiRelationship
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.DOCUMENTS_PATH
import no.elhub.auth.features.documents.common.dto.AuthorizationDocumentResponseAttributes
import no.elhub.auth.features.documents.common.dto.AuthorizationDocumentResponseLinks
import no.elhub.auth.features.documents.common.dto.AuthorizationDocumentResponseRelationships
import no.elhub.auth.features.grants.GRANTS_PATH
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiMeta
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks

typealias GetDocumentSingleResponse = JsonApiResponse.SingleDocumentWithRelationshipsAndMetaAndLinks<
    AuthorizationDocumentResponseAttributes,
    AuthorizationDocumentResponseRelationships,
    JsonApiResourceMetaMap,
    AuthorizationDocumentResponseLinks
    >

fun AuthorizationDocument.toGetSingleResponse() =
    GetDocumentSingleResponse(
        data = JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
            type = "AuthorizationDocument",
            id = this.id.toString(),
            attributes = AuthorizationDocumentResponseAttributes(
                status = this.status.name,
                documentType = this.type.name,
                validTo = this.validTo.toTimeZoneOffsetString(),
                createdAt = this.createdAt.toTimeZoneOffsetString(),
                updatedAt = this.createdAt.toTimeZoneOffsetString(),
            ),
            relationships = AuthorizationDocumentResponseRelationships(
                requestedBy = this.requestedBy.toJsonApiRelationship(),
                requestedFrom = this.requestedFrom.toJsonApiRelationship(),
                requestedTo = this.requestedTo.toJsonApiRelationship(),
                signedBy = this.signedBy?.let {
                    JsonApiRelationshipToOne(
                        data = JsonApiRelationshipData(
                            type = it.type.name,
                            id = it.id
                        )
                    )
                },
                authorizationGrant = this.grantId?.let {
                    JsonApiRelationshipToOne(
                        data = JsonApiRelationshipData(
                            id = it.toString(),
                            type = "AuthorizationGrant"
                        ),
                        links = JsonApiLinks.RelationShipLink(
                            self = "$GRANTS_PATH/$it"
                        )
                    )
                },
            ),
            meta = JsonApiResourceMetaMap(
                buildMap {
                    this@toGetSingleResponse.properties.forEach {
                        put(it.key, it.value)
                    }
                }
            ),
            links = AuthorizationDocumentResponseLinks(
                self = "${DOCUMENTS_PATH}/${this.id}",
                file = "${DOCUMENTS_PATH}/${this.id}.pdf"
            )
        ),
        links = JsonApiLinks.ResourceObjectLink(DOCUMENTS_PATH),
        meta = JsonApiMeta(
            buildJsonObject {
                put("createdAt", this@toGetSingleResponse.createdAt.toTimeZoneOffsetString())
            }
        )
    )
