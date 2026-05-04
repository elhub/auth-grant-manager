package no.elhub.auth.features.documents.query.dto

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.features.common.Page
import no.elhub.auth.features.common.currentTimeOslo
import no.elhub.auth.features.common.dto.JsonApiResourceMetaMap
import no.elhub.auth.features.common.dto.PaginatedCollectionResponse
import no.elhub.auth.features.common.toPaginationLinks
import no.elhub.auth.features.common.toTimeZoneOffsetString
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.DOCUMENTS_PATH
import no.elhub.auth.features.documents.common.dto.AuthorizationDocumentResponseAttributes
import no.elhub.auth.features.documents.common.dto.AuthorizationDocumentResponseLinks
import no.elhub.auth.features.documents.common.dto.AuthorizationDocumentResponseRelationships
import no.elhub.auth.features.documents.get.dto.toGetSingleResponse
import no.elhub.devxp.jsonapi.response.JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks

typealias GetDocumentCollectionResponse = PaginatedCollectionResponse<
    JsonApiResponseResourceObjectWithRelationshipsAndMetaAndLinks<
        AuthorizationDocumentResponseAttributes,
        AuthorizationDocumentResponseRelationships,
        JsonApiResourceMetaMap,
        AuthorizationDocumentResponseLinks
        >
    >

fun Page<AuthorizationDocument>.toGetCollectionResponse(
    statuses: List<AuthorizationDocument.Status> = emptyList(),
): GetDocumentCollectionResponse {
    val p = this.pagination
    val extraParams = if (statuses.isEmpty()) emptyMap()
        else mapOf("filter[status]" to statuses.joinToString(","))

    return GetDocumentCollectionResponse(
        data = this.items.map { it.toGetSingleResponse().data },
        links = toPaginationLinks(DOCUMENTS_PATH, extraParams),
        meta = buildJsonObject {
            put("createdAt", currentTimeOslo().toTimeZoneOffsetString())
            put("totalItems", this@toGetCollectionResponse.totalItems)
            put("totalPages", this@toGetCollectionResponse.totalPages)
            put("page", p.page)
            put("pageSize", p.size)
        }
    )
}
