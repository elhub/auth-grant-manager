package no.elhub.auth.v1.api.requests.response

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.dto.PaginatedCollectionResponse
import no.elhub.auth.v0.features.common.toPaginationLinks
import no.elhub.auth.v1.V1_REQUESTS_PATH
import no.elhub.auth.v1.common.now
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestStatus

typealias JsonApiListAuthorizationRequestsResponse = PaginatedCollectionResponse<AuthorizationRequestResource>

fun Page<AuthorizationRequest>.toGetCollectionResponse(
    statuses: List<AuthorizationRequestStatus> = emptyList(),
): JsonApiListAuthorizationRequestsResponse {
    val extraParams = if (statuses.isEmpty()) {
        emptyMap()
    } else {
        mapOf("filter[status]" to statuses.joinToString(","))
    }

    return JsonApiListAuthorizationRequestsResponse(
        data = items.map { it.toGetResponse().data },
        links = toPaginationLinks(V1_REQUESTS_PATH, extraParams),
        meta = buildJsonObject {
            put("createdAt", now().toString())
            put("totalItems", totalItems)
            put("totalPages", totalPages)
            put("page", pagination.page)
            put("pageSize", pagination.size)
        },
    )
}
