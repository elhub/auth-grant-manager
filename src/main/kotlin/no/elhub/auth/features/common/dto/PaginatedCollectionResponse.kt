package no.elhub.auth.features.common.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.features.common.PaginationLinks

/**
 * A generic JSON:API-shaped collection response with pagination links and meta.
 *
 * Used in place of the devxp library's collection types which only support a `self` link.
 */
@Serializable
data class PaginatedCollectionResponse<DATA>(
    val data: List<DATA>,
    val links: PaginationLinks,
    val meta: JsonObject,
)
