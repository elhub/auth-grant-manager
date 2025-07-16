package no.elhub.auth.grantmanager.presentation

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

    fun String.shouldBeList(
        size: Int? = null,
        block: JsonArrayValidationContext.() -> Unit,
    ) {
        val maybeArray = json[this] ?: error("Key '$this' not found in JSON: $json")
        if (maybeArray !is JsonArray) {
            error("Expected key '$this' to be a JsonArray, but found: $maybeArray")
        }
        if (size != null && maybeArray.size != size) {
            error("Expected array at key '$this' to have size $size, but found ${maybeArray.size}")
        }
        // Mark this key as validated
        validatedKeys += this
        // Delegate into JsonArrayValidationContext
        JsonArrayValidationContext(maybeArray).apply(block)
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
}

infix fun JsonObject.validate(block: JsonValidationContext.() -> Unit) {
    JsonValidationContext.validate(this) {
        block()
    }
}

class JsonArrayValidationContext(
    private val array: JsonArray,
) {
    /**
     * For each element in the array, assert that it's a JsonObject, then
     * run the given block inside a fresh JsonValidationContext.
     */
    fun item(
        index: Int,
        block: JsonValidationContext.() -> Unit,
    ) {
        val element = array[index]
        if (element !is JsonObject) {
            error("Expected element at index $index to be a JSON object, but found: $element")
        }
        JsonValidationContext(element).apply(block)
    }
}
