package no.elhub.auth.features.documents.query.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.party.dto.toJsonApiRelationship
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.DOCUMENTS_PATH
import no.elhub.auth.features.grants.GRANTS_PATH
import no.elhub.auth.features.requests.REQUESTS_PATH
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
data class GetDocumentCollectionResponseAttributes(
    val status: String,
    val documentType: String
) : JsonApiAttributes

@Serializable
data class GetDocumentCollectionResponseRelationships(
    val requestedBy: JsonApiRelationshipToOne,
    val requestedFrom: JsonApiRelationshipToOne,
    val requestedTo: JsonApiRelationshipToOne,
    val signedBy: JsonApiRelationshipToOne? = null,
    val grant: JsonApiRelationshipToOne? = null
) : JsonApiRelationships

@Serializable
@JvmInline
value class GetDocumentCollectionResponseMeta(
    val values: Map<String, String>
) : JsonApiResourceMeta

@Serializable
data class GetDocumentCollectionResponseLinks(
    val self: String,
    val file: String,
) : JsonApiResourceLinks

typealias GetDocumentCollectionResponse = JsonApiResponse.CollectionDocumentWithRelationshipsAndMetaAndLinks<
    GetDocumentCollectionResponseAttributes,
    GetDocumentCollectionResponseRelationships,
    GetDocumentCollectionResponseMeta,
    GetDocumentCollectionResponseLinks
    >

fun List<AuthorizationDocument>.toGetCollectionResponse() =
    GetDocumentCollectionResponse(
        data = this.map { document ->
            JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks(
                id = document.id.toString(),
                type = "AuthorizationDocument",
                attributes = GetDocumentCollectionResponseAttributes(
                    status = document.status.name,
                    documentType = document.type.name,
                ),
                relationships = GetDocumentCollectionResponseRelationships(
                    requestedBy = document.requestedBy.toJsonApiRelationship(),
                    requestedFrom = document.requestedFrom.toJsonApiRelationship(),
                    requestedTo = document.requestedTo.toJsonApiRelationship(),
                    signedBy = document.signedBy?.let {
                        JsonApiRelationshipToOne(
                            data = JsonApiRelationshipData(
                                type = it.type.name,
                                id = it.resourceId
                            )
                        )
                    },
                    grant = document.grantId?.let {
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
                meta = GetDocumentCollectionResponseMeta(
                    buildMap {
                        put("createdAt", document.createdAt.toTimeZoneOffsetString())
                        put("updatedAt", document.updatedAt.toTimeZoneOffsetString())
                        document.properties.forEach {
                            put(it.key, (it.value))
                        }
                    }
                ),
                links = GetDocumentCollectionResponseLinks(
                    self = "${DOCUMENTS_PATH}/${document.id}",
                    file = "${DOCUMENTS_PATH}/${document.id}.pdf"
                )
            )
        },
        links = JsonApiLinks.ResourceObjectLink(DOCUMENTS_PATH),
        meta = JsonApiMeta(
            buildJsonObject {
                put("createdAt", currentTimeWithTimeZone().toTimeZoneOffsetString())
            }
        )
    )
