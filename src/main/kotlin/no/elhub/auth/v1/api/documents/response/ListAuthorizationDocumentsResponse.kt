package no.elhub.auth.v1.api.documents.response

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.dto.PaginatedCollectionResponse
import no.elhub.auth.v0.features.common.toPaginationLinks
import no.elhub.auth.v1.V1_DOCUMENTS_PATH
import no.elhub.auth.v1.common.now
import no.elhub.auth.v1.domain.AuthorizationDocument

typealias JsonApiListAuthorizationDocumentsResponse = PaginatedCollectionResponse<GetAuthorizationDocumentResource>

fun Page<AuthorizationDocument>.toGetCollectionResponse(
    statuses: List<ResponseAuthorizationDocumentStatus> = emptyList(),
): JsonApiListAuthorizationDocumentsResponse {
    val extraParams = if (statuses.isEmpty()) {
        emptyMap()
    } else {
        mapOf("filter[status]" to statuses.joinToString(","))
    }

    return JsonApiListAuthorizationDocumentsResponse(
        data = items.map { it.toGetResponse().data },
        links = toPaginationLinks(V1_DOCUMENTS_PATH, extraParams),
        meta = buildJsonObject {
            put("createdAt", now().toString())
            put("totalItems", totalItems)
            put("totalPages", totalPages)
            put("page", pagination.page)
            put("pageSize", pagination.size)
        },
    )
}
