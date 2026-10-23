package no.elhub.auth.v1.api.grants.response

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.dto.PaginatedCollectionResponse
import no.elhub.auth.v0.features.common.toPaginationLinks
import no.elhub.auth.v1.V1_GRANTS_PATH
import no.elhub.auth.v1.common.now
import no.elhub.auth.v1.domain.AuthorizationGrant

typealias JsonApiListAuthorizationGrantsResponse = PaginatedCollectionResponse<AuthorizationGrantResource>

fun Page<AuthorizationGrant>.toGetCollectionResponse(): JsonApiListAuthorizationGrantsResponse =
    JsonApiListAuthorizationGrantsResponse(
        data = items.map { it.toGetResponse().data },
        links = toPaginationLinks(V1_GRANTS_PATH),
        meta = buildJsonObject {
            put("createdAt", now().toString())
            put("totalItems", totalItems)
            put("totalPages", totalPages)
            put("page", pagination.page)
            put("pageSize", pagination.size)
        },
    )
