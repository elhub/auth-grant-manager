package no.elhub.auth.features.documents.query.dto

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.currentTimeLocal
import no.elhub.auth.features.common.dto.JsonApiResourceMetaMap
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.DOCUMENTS_PATH
import no.elhub.auth.features.documents.common.dto.AuthorizationDocumentResponseAttributes
import no.elhub.auth.features.documents.common.dto.AuthorizationDocumentResponseLinks
import no.elhub.auth.features.documents.common.dto.AuthorizationDocumentResponseRelationships
import no.elhub.auth.features.documents.get.dto.toGetSingleResponse
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.model.JsonApiMeta
import no.elhub.devxp.jsonapi.response.JsonApiResponse

typealias GetDocumentCollectionResponse = JsonApiResponse.CollectionDocumentWithRelationshipsAndMetaAndLinks<
    AuthorizationDocumentResponseAttributes,
    AuthorizationDocumentResponseRelationships,
    JsonApiResourceMetaMap,
    AuthorizationDocumentResponseLinks
    >

fun List<AuthorizationDocument>.toGetCollectionResponse() = GetDocumentCollectionResponse(
    data = this.map {
        it.toGetSingleResponse().data
    },
    links = JsonApiLinks.ResourceObjectLink(DOCUMENTS_PATH),
    meta = JsonApiMeta(
        buildJsonObject {
            put("createdAt", currentTimeLocal().toTimeZoneOffsetString())
        }
    )
)
