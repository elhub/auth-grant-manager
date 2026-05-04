package no.elhub.auth.features.common

import arrow.core.Either
import arrow.core.right
import kotlin.collections.distinct

// Returns a list of enum values from a comma-separated string,
// or left if string has incorrect format
inline fun <reified T : Enum<T>> validateEnumListParam(
    param: String?,
    paramName: String,
): Either<InputError.MalformedInputError, List<T>> =
    Either.catch {
        if (param.isNullOrBlank()) {
            return emptyList<T>().right()
        }
        param.split(',').map { enumValueOf<T>(it) }.distinct()
    }.mapLeft {
        InputError.MalformedInputError(
            "Invalid $paramName value '$param'. Valid values: ${enumValues<T>().joinToString()}"
        )
    }
