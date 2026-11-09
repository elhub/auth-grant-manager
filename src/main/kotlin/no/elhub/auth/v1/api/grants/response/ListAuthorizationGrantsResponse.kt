package no.elhub.auth.v1.api.grants.response

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.dto.PaginatedCollectionResponse
import no.elhub.auth.v0.features.common.toPaginationLinks
import no.elhub.auth.v1.V1_GRANTS_PATH
import no.elhub.auth.v1.api.grants.routes.STATUS_FILTER_PARAM
import no.elhub.auth.v1.common.now
import no.elhub.auth.v1.common.toOsloOffsetString
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantStatus

typealias JsonApiListAuthorizationGrantsResponse = PaginatedCollectionResponse<AuthorizationGrantResource>

fun Page<AuthorizationGrant>.toGetCollectionResponse(
    statuses: List<AuthorizationGrantStatus> = emptyList(),
): JsonApiListAuthorizationGrantsResponse {
    val extraParams = if (statuses.isEmpty()) {
        emptyMap()
    } else {
        mapOf(STATUS_FILTER_PARAM to statuses.joinToString(","))
    }

    return JsonApiListAuthorizationGrantsResponse(
        data = items.map { it.toGetResponse().data },
        links = toPaginationLinks(V1_GRANTS_PATH, extraParams),
        meta = buildJsonObject {
            put("createdAt", now().toOsloOffsetString())
            put("totalItems", totalItems)
            put("totalPages", totalPages)
            put("page", pagination.page)
            put("pageSize", pagination.size)
        },
    )
}
