package no.elhub.auth

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.kotest.matchers.shouldBe as kotestShouldBe
import io.kotest.matchers.nulls.shouldNotBeNull as kotestShouldNotBeNull


@DslMarker
annotation class JsonDsl

@JsonDsl
class JsonValidationContext(private val json: JsonObject) {

    // Track keys that have been validated
    val validatedKeys = mutableSetOf<String>()

    fun assertAllKeysValidated() {
        val unvalidated = json.keys - validatedKeys
        if (unvalidated.isNotEmpty()) {
            error("Unvalidated keys in JSON: $unvalidated")
        }
    }

    operator fun String.invoke(block: JsonValidationContext.() -> Unit) {
        validatedKeys += this
        val child = json[this] ?: error("Key '$this' not found in JSON: $json")
        if (child !is JsonObject) {
            error("Expected key '$this' to be a JsonObject, but found: $child")
        }
        JsonValidationContext(child).apply {
            block()
            assertAllKeysValidated()
        }
    }

    infix fun String.shouldBe(expected: Any?) {
        validatedKeys += this
        val actual = json[this] ?: error("Key '$this' not found in JSON: $json")
        actual.jsonPrimitive.content kotestShouldBe expected
    }

    fun String.shouldNotBeNull() {
        validatedKeys += this
        val actual = json[this] ?: error("Key '$this' not found in JSON: $json")
        actual.jsonPrimitive.content.kotestShouldNotBeNull()
    }
}

fun JsonObject.validate(block: JsonValidationContext.() -> Unit) {
    JsonValidationContext(this).apply {
        block()
        print(this.validatedKeys)
        assertAllKeysValidated()
    }
}
