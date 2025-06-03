package no.elhub.auth

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.kotest.matchers.nulls.shouldNotBeNull as kotestShouldNotBeNull
import io.kotest.matchers.shouldBe as kotestShouldBe

@DslMarker
annotation class JsonDsl

@JsonDsl
class JsonValidationContext(
    private val json: JsonObject,
) {
    companion object {
        fun validate(
            json: JsonObject,
            block: JsonValidationContext.() -> Unit,
        ) {
            JsonValidationContext(json).apply {
                block()
                assertAllKeysValidated()
            }
        }
    }

    private val validatedKeys = mutableSetOf<String>()

    private fun assertAllKeysValidated() {
        val unvalidated = json.keys - validatedKeys
        if (unvalidated.isNotEmpty()) {
            error("Unvalidated keys in JSON: $unvalidated")
        }
    }

    fun String.validateArray(block: (JsonObject, Any?) -> Unit) {
        val arr = json[this] ?: error("Key '$this' not found in JSON: $json")
        if (arr !is JsonArray) error("Expected key '$this' to be a JsonArray, but found $arr")
        validatedKeys += this
        arr.forEachIndexed { idx, jsonElement ->
            if (jsonElement is JsonObject) {
                block(jsonElement, idx)
            } else {
                error("Expected array element to be JsonObject, but found: $jsonElement")
            }
        }
    }

    operator fun String.invoke(block: JsonValidationContext.() -> Unit) {
        val child = json[this] ?: error("Key '$this' not found in JSON: $json")
        if (child !is JsonObject) {
            error("Expected key '$this' to be a JsonObject, but found: $child")
        }
        validatedKeys += this
        validate(child) {
            block()
        }
    }

    infix fun String.shouldBe(expected: Any?) {
        val actual = json[this] ?: error("Key '$this' not found in JSON: $json")
        actual.jsonPrimitive.content kotestShouldBe expected
        validatedKeys += this
    }

    fun String.shouldNotBeNull() {
        val actual = json[this] ?: error("Key '$this' not found in JSON: $json")
        actual.jsonPrimitive.content.kotestShouldNotBeNull()
        validatedKeys += this
    }

    infix fun String.validate(block: JsonValidationContext.() -> Unit) {
        val obj = json[this] ?: error("Key '$this' not found in JSON: $json")
        if (obj !is JsonObject) error("Expected key '$this' to be a JsonObject, but found $obj")
        validatedKeys += this
        JsonValidationContext(obj).apply(block)
    }
}

infix fun JsonObject.validate(block: JsonValidationContext.() -> Unit) {
    JsonValidationContext.validate(this) {
        block()
    }
}
