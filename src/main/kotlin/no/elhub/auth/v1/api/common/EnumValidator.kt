package no.elhub.auth.v1.api.common

import no.elhub.auth.v1.api.common.errors.InputError

inline fun <reified T : Enum<T>> validateEnumListParam(
    param: String?,
    paramName: String,
): List<T> {
    if (param.isNullOrBlank()) return emptyList()

    return try {
        param.split(',').map { enumValueOf<T>(it) }.distinct()
    } catch (_: IllegalArgumentException) {
        throw InputError.InvalidQueryParameterError(
            "Invalid $paramName value '$param'. Valid values: ${enumValues<T>().joinToString()}"
        )
    }
}
