package no.elhub.auth.features.documents.get.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.party.dto.toJsonApiRelationship
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.DOCUMENTS_PATH
import no.elhub.auth.features.grants.GRANTS_PATH
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
data class GetDocumentSingleResponseAttributes(
    val status: String,
    val documentType: String,
    val validTo: String,
    val createdAt: String,
    val updatedAt: String
) : JsonApiAttributes

@Serializable
data class GetDocumentSingleResponseRelationship(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
    val requestedTo: JsonApiRelationshipToOne,
    val signedBy: JsonApiRelationshipToOne? = null,
    val authorizationGrant: JsonApiRelationshipToOne? = null
) : JsonApiRelationships

@Serializable
@JvmInline
value class GetDocumentSingleResponseMeta(
    val values: Map<String, String>
) : JsonApiResourceMeta

@Serializable
data class GetDocumentSingleResponseLinks(
    val self: String,
    val file: String,
) : JsonApiResourceLinks

typealias GetDocumentSingleResponse = JsonApiResponse.SingleDocumentWithRelationshipsAndMetaAndLinks<
    GetDocumentSingleResponseAttributes,
    GetDocumentSingleResponseRelationship,
    GetDocumentSingleResponseMeta,
    GetDocumentSingleResponseLinks
    >

fun AuthorizationDocument.toGetSingleResponse() =
    GetDocumentSingleResponse(
        data = JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
            type = "AuthorizationDocument",
            id = this.id.toString(),
            attributes = GetDocumentSingleResponseAttributes(
                status = this.status.name,
                documentType = this.type.name,
                validTo = this.validTo.toTimeZoneOffsetString(),
                createdAt = requireNotNull(this.createdAt?.toTimeZoneOffsetString()) { "createdAt not set!" },
                updatedAt = requireNotNull(this.updatedAt?.toTimeZoneOffsetString()) { "updatedAt not set!" },
            ),
            relationships = GetDocumentSingleResponseRelationship(
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
            meta = GetDocumentSingleResponseMeta(
                buildMap {
                    this@toGetSingleResponse.properties.forEach {
                        put(it.key, (it.value))
                    }
                }
            ),
            links = GetDocumentSingleResponseLinks(
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
