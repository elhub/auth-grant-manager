package no.elhub.auth.features.common

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

internal class InvalidStoredMetadataException(message: String) : IllegalStateException(message)

internal fun toResponseMetadata(
    entries: List<Pair<String, JsonElement>>,
    allowedKeys: Set<String>,
    context: String,
): AuthorizationMetadata = entries.associate { (key, value) ->
    if (key !in allowedKeys) {
        throw InvalidStoredMetadataException(
            "Stored metadata key '$key' is not valid for '$context'"
        )
    }
    if (value !is JsonPrimitive || !value.isString) {
        throw InvalidStoredMetadataException(
            "Stored metadata key '$key' must be a string for '$context'"
        )
    }
    key to value
}
