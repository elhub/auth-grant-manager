package no.elhub.auth.v1.api.grants.response

import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.dto.PaginatedCollectionResponse
import no.elhub.auth.v0.features.common.toPaginationLinks
import no.elhub.auth.v1.V1_GRANTS_PATH
import no.elhub.auth.v1.api.grants.routes.APPROVED_BY_FILTER_PARAM
import no.elhub.auth.v1.api.grants.routes.GRANTED_BY_FILTER_PARAM
import no.elhub.auth.v1.api.grants.routes.GRANTED_TO_FILTER_PARAM
import no.elhub.auth.v1.api.grants.routes.PRINCIPAL_FILTER_PARAM
import no.elhub.auth.v1.api.grants.routes.STATUS_FILTER_PARAM
import no.elhub.auth.v1.common.now
import no.elhub.auth.v1.common.toOsloOffsetString
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.GrantPartyFilters

typealias JsonApiListAuthorizationGrantsResponse = PaginatedCollectionResponse<AuthorizationGrantResource>

fun Page<AuthorizationGrant>.toGetCollectionResponse(
    statuses: List<AuthorizationGrantStatus> = emptyList(),
    partyFilters: GrantPartyFilters = GrantPartyFilters(),
): JsonApiListAuthorizationGrantsResponse {
    val extraParams = buildMap {
        if (statuses.isNotEmpty()) put(STATUS_FILTER_PARAM, statuses.joinToString(","))
        listOf(
            GRANTED_BY_FILTER_PARAM to partyFilters.grantedBy,
            PRINCIPAL_FILTER_PARAM to partyFilters.principal,
            APPROVED_BY_FILTER_PARAM to partyFilters.approvedBy,
            GRANTED_TO_FILTER_PARAM to partyFilters.grantedTo,
        ).forEach { (parameter, ids) ->
            if (ids.isNotEmpty()) put(parameter, ids.joinToString(",") { it.encodeURLParameter() })
        }
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
