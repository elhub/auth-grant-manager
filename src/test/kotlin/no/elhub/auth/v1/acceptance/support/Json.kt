package no.elhub.auth.v1.acceptance.support

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Reads a value by dotted path, e.g. `data.relationships.requestedBy.data.id` or `data.attributes.requestedScopes[0]`. */
fun JsonElement.at(path: String): JsonElement? =
    segments(path).fold(this as JsonElement?) { current, segment ->
        when {
            current == null || current is JsonNull -> null
            segment is Int -> (current as? JsonArray)?.getOrNull(segment)
            else -> (current as? JsonObject)?.get(segment as String)
        }
    }

fun JsonElement.text(path: String): String? = (at(path) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

private fun segments(path: String): List<Any> =
    path.split('.').flatMap { part ->
        val name = part.substringBefore('[')
        val indexes = Regex("\\[(\\d+)]").findAll(part).map { it.groupValues[1].toInt() }.toList()
        listOfNotNull(name.ifEmpty { null }) + indexes
    }
