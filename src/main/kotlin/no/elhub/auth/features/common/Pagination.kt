package no.elhub.auth.features.common

import kotlin.math.ceil

const val DEFAULT_PAGE_SIZE = 30
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
