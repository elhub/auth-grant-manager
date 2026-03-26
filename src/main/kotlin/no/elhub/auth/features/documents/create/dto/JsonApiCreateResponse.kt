package no.elhub.auth.features.documents.create.dto

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.dto.JsonApiResourceMetaMap
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.DOCUMENTS_PATH
import no.elhub.auth.features.documents.common.dto.AuthorizationDocumentResponseAttributes
import no.elhub.auth.features.documents.common.dto.AuthorizationDocumentResponseLinks
import no.elhub.auth.features.documents.common.dto.AuthorizationDocumentResponseRelationships
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiMeta
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks

typealias CreateDocumentResponse = JsonApiResponse.SingleDocumentWithRelationshipsAndMetaAndLinks<
    AuthorizationDocumentResponseAttributes,
    AuthorizationDocumentResponseRelationships,
    JsonApiResourceMetaMap,
    AuthorizationDocumentResponseLinks
    >

fun AuthorizationDocument.toCreateDocumentResponse() = CreateDocumentResponse(
    data = JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
        type = "AuthorizationDocument",
        id = this.id.toString(),
        attributes = AuthorizationDocumentResponseAttributes(
            status = this.status.name,
            documentType = this.type.name,
            validTo = this.validTo.toTimeZoneOffsetString(),
            createdAt = this.createdAt.toTimeZoneOffsetString(),
            updatedAt = this.updatedAt.toTimeZoneOffsetString(),
        ),
        relationships = AuthorizationDocumentResponseRelationships(
            requestedBy = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    type = this.requestedBy.type.name,
                    id = this.requestedBy.id,
                )
            ),
            requestedFrom = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    type = this.requestedFrom.type.name,
                    id = this.requestedFrom.id,
                )
            ),
            requestedTo = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    type = this.requestedTo.type.name,
                    id = this.requestedTo.id,
                )
            )
        ),
        meta = JsonApiResourceMetaMap(
            buildMap {
                this@toCreateDocumentResponse.properties.forEach { properties ->
                    put(properties.key, properties.value)
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
            put("createdAt", this@toCreateDocumentResponse.createdAt.toTimeZoneOffsetString())
        }
    )
)
