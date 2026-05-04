package no.elhub.auth.features.common

import kotlinx.serialization.Serializable
import no.elhub.devxp.jsonapi.model.JsonApiResourceLinks
import kotlin.math.ceil

const val DEFAULT_PAGE_SIZE = 100
const val MAX_PAGE_SIZE = 100

data class Pagination(
    val page: Int = 0,
    val size: Int = DEFAULT_PAGE_SIZE,
) {
    val offset: Long get() = page.toLong() * size

    companion object {
        fun from(pageParam: String?, sizeParam: String?): Pagination {
            val size = sizeParam?.toIntOrNull()?.coerceIn(1, MAX_PAGE_SIZE) ?: DEFAULT_PAGE_SIZE
            val page = pageParam?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            return Pagination(page = page, size = size)
        }
    }
}

data class Page<T>(
    val items: List<T>,
    val totalItems: Long,
    val pagination: Pagination,
) {
    val totalPages: Int = if (pagination.size == 0) 0 else ceil(totalItems.toDouble() / pagination.size).toInt()
}

@Serializable
data class PaginationLinks(
    val self: String,
    val first: String,
    val last: String,
    val prev: String? = null,
    val next: String? = null,
) : JsonApiResourceLinks

fun <T> Page<T>.toPaginationLinks(
    basePath: String,
    extraParams: Map<String, String> = emptyMap(),
): PaginationLinks {
    val p = pagination
    val lastPage = (totalPages - 1).coerceAtLeast(0)
    val extra = extraParams.entries.joinToString("") { (k, v) -> "&$k=$v" }
    fun pageUrl(pageNum: Int) = "$basePath?page[number]=$pageNum&page[size]=${p.size}$extra"
    return PaginationLinks(
        self = pageUrl(p.page),
        first = pageUrl(0),
        last = pageUrl(lastPage),
        prev = if (p.page > 0) pageUrl(p.page - 1) else null,
        next = if (p.page < lastPage) pageUrl(p.page + 1) else null,
    )
}
