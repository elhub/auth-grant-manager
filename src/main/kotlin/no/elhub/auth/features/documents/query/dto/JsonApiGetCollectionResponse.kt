package no.elhub.auth.features.documents.query.dto

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.DOCUMENTS_PATH
import no.elhub.auth.features.documents.get.dto.GetDocumentSingleResponseAttributes
import no.elhub.auth.features.documents.get.dto.GetDocumentSingleResponseLinks
import no.elhub.auth.features.documents.get.dto.GetDocumentSingleResponseMeta
import no.elhub.auth.features.documents.get.dto.GetDocumentSingleResponseRelationship
import no.elhub.auth.features.documents.get.dto.toGetSingleResponse
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiMeta
import no.elhub.devxp.jsonapi.response.JsonApiResponse

typealias GetDocumentCollectionResponse = JsonApiResponse.CollectionDocumentWithRelationshipsAndMetaAndLinks<
    GetDocumentSingleResponseAttributes,
    GetDocumentSingleResponseRelationship,
    GetDocumentSingleResponseMeta,
    GetDocumentSingleResponseLinks
    >

fun List<AuthorizationDocument>.toGetCollectionResponse() = GetDocumentCollectionResponse(
    data = this.map {
        it.toGetSingleResponse().data
    },
    links = JsonApiLinks.ResourceObjectLink(DOCUMENTS_PATH),
    meta = JsonApiMeta(
        buildJsonObject {
            put("createdAt", currentTimeWithTimeZone().toTimeZoneOffsetString())
        }
    )
)
