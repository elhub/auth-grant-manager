package no.elhub.auth

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.kotest.matchers.nulls.shouldNotBeNull as kotestShouldNotBeNull
import io.kotest.matchers.shouldBe as kotestShouldBe

@DslMarker
annotation class JsonDsl

@JsonDsl
class JsonValidationContext(private val json: JsonObject) {

    companion object {
        fun validate(json: JsonObject, block: JsonValidationContext.() -> Unit) {
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

fun JsonObject.validate(block: JsonValidationContext.() -> Unit) {
    JsonValidationContext.validate(this) {
        block()
    }
}
