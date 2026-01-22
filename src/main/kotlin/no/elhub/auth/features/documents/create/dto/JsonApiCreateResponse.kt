package no.elhub.auth.features.documents.create.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.DOCUMENTS_PATH
import no.elhub.devxp.jsonapi.model.JsonApiAttributes
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiMeta
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.model.JsonApiRelationships
import no.elhub.devxp.jsonapi.model.JsonApiResourceLinks
import no.elhub.devxp.jsonapi.model.JsonApiResourceMeta
import no.elhub.devxp.jsonapi.response.JsonApiResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks

@Serializable
data class CreateDocumentResponseAttributes(
    val status: String,
    val documentType: String,
    val validTo: String,
    val createdAt: String,
    val updatedAt: String,
) : JsonApiAttributes

@Serializable
data class CreateDocumentResponseRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
    val requestedTo: JsonApiRelationshipToOne,
    val signedBy: JsonApiRelationshipToOne? = null,
    val grant: JsonApiRelationshipToOne? = null,
) : JsonApiRelationships

@Serializable
@JvmInline
value class CreateDocumentResponseMeta(
    val values: Map<String, String>
) : JsonApiResourceMeta

@Serializable
data class CreateDocumentResponseLinks(
    val self: String,
    val file: String,
) : JsonApiResourceLinks

typealias CreateDocumentResponse = JsonApiResponse.SingleDocumentWithRelationshipsAndMetaAndLinks<
        CreateDocumentResponseAttributes,
        CreateDocumentResponseRelationships,
        CreateDocumentResponseMeta,
        CreateDocumentResponseLinks
        >

fun AuthorizationDocument.toCreateDocumentResponse() = CreateDocumentResponse(
    data = JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
        type = "AuthorizationDocument",
        id = this.id.toString(),
        attributes = CreateDocumentResponseAttributes(
            status = this.status.name,
            documentType = this.type.name,
            validTo = this.validTo.toTimeZoneOffsetString(),
            createdAt = this.createdAt.toTimeZoneOffsetString(),
            updatedAt = this.updatedAt.toTimeZoneOffsetString(),
        ),
        relationships = CreateDocumentResponseRelationships(
            requestedBy = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    type = this.requestedBy.type.name,
                    id = this.requestedBy.resourceId,
                )
            ),
            requestedFrom = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    type = this.requestedFrom.type.name,
                    id = this.requestedFrom.resourceId,
                )
            ),
            requestedTo = JsonApiRelationshipToOne(
                data = JsonApiRelationshipData(
                    type = this.requestedTo.type.name,
                    id = this.requestedTo.resourceId,
                )
            )
        ),
        meta = CreateDocumentResponseMeta(
            buildMap {
                this@toCreateDocumentResponse.properties.forEach { properties ->
                    put(properties.key, properties.value)
                }
            }
        ),
        links = CreateDocumentResponseLinks(
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
