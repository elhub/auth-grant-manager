package no.elhub.auth.v1.acceptance.support

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray

class ApiResponse(val status: HttpStatusCode, private val bytes: ByteArray, val contentType: ContentType?) {
    val json: JsonElement by lazy { Json.parseToJsonElement(bytes.decodeToString()) }
    val bodyBytes: ByteArray get() = bytes

    fun field(path: String): String? = json.text(path)

    fun singleRelatedId(relationship: String): String {
        val path = "data.relationships.$relationship.data"
        val related = requireNotNull(json.at(path)) { "Missing relationship '$relationship' in response" }.jsonArray
        check(related.size == 1) { "Expected one '$relationship' entry, got ${related.size}" }
        return requireNotNull(related.single().text("id")) { "Missing id in '$relationship' entry" }
    }

    fun shouldHaveStatus(expected: HttpStatusCode) = apply {
        withClue("Response body: ${bytes.decodeToString().take(2000)}") { status shouldBe expected }
    }

    fun shouldHaveField(path: String, expected: String?) = apply {
        withClue("Field '$path'") { field(path) shouldBe expected }
    }

    /** Asserts that the JSON at [path] equals [expected], ignoring formatting and key order. */
    fun shouldHaveJson(path: String, expected: String) = apply {
        withClue("Field '$path'") { json.at(path).toString() shouldEqualJson expected.trimIndent() }
    }

    fun shouldBeError(expected: HttpStatusCode, detailContains: String) = apply {
        shouldHaveStatus(expected)
        withClue("Error detail") { field("errors[0].detail").orEmpty() shouldContain detailContains }
    }
}
