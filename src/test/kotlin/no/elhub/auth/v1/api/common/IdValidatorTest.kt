package no.elhub.auth.v1.api.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.elhub.auth.v1.api.common.errors.InputError
import kotlin.uuid.Uuid

class IdValidatorTest : FunSpec({
    val id = Uuid.parse("123e4567-e89b-12d3-a456-426614174000")

    test("path IDs parse to Kotlin UUIDs regardless of hex case") {
        validatePathId(id.toString().uppercase()) shouldBe id
    }

    test("throws MissingIdError for missing path IDs and MalformedIdError for invalid UUIDs") {
        listOf(null, "", " ").forEach {
            shouldThrow<InputError.MissingIdError> { validatePathId(it) }
        }
        shouldThrow<InputError.MalformedIdError> { validatePathId("not-a-uuid") }
    }

    test("body IDs compare parsed UUIDs rather than string case") {
        validateDataId(id.toString().uppercase(), id) shouldBe id
    }

    test("throws MissingIdError for missing body IDs and DataIdMismatchError for invalid or mismatched UUIDs") {
        listOf(null, "", " ").forEach {
            shouldThrow<InputError.MissingIdError> { validateDataId(it, id) }
        }
        listOf("not-a-uuid", Uuid.NIL.toString()).forEach {
            shouldThrow<InputError.DataIdMismatchError> { validateDataId(it, id) }
        }
    }
})
