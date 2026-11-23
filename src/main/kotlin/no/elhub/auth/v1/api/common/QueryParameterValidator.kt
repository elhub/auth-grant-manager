package no.elhub.auth.v1.api.common

import io.ktor.server.application.ApplicationCall
import no.elhub.auth.v0.features.common.DEFAULT_PAGE_SIZE
import no.elhub.auth.v0.features.common.MAX_PAGE_SIZE
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.api.common.errors.InputError

fun ApplicationCall.validateQueryParameters(vararg supported: String) {
    request.queryParameters.names().firstOrNull { it !in supported }?.let { parameter ->
        throw InputError.InvalidQueryParameterError("Unsupported query parameter '$parameter'.")
    }
}

internal fun ApplicationCall.parsePagination(): Pagination {
    val size = request.queryParameters[PAGE_SIZE_PARAM]?.let { value ->
        value.toIntOrNull()?.takeIf { it in 1..MAX_PAGE_SIZE }
            ?: throw InputError.InvalidQueryParameterError("$PAGE_SIZE_PARAM must be an integer between 1 and $MAX_PAGE_SIZE.")
    } ?: DEFAULT_PAGE_SIZE
    val page = request.queryParameters[PAGE_NUMBER_PARAM]?.let { value ->
        value.toIntOrNull()?.takeIf { it >= 0 }
            ?: throw InputError.InvalidQueryParameterError("$PAGE_NUMBER_PARAM must be a non-negative integer.")
    } ?: 0
    return Pagination(page = page, size = size)
}
