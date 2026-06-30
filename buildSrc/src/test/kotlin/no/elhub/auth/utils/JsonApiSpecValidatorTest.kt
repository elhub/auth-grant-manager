package no.elhub.auth.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.nio.file.Files

class JsonApiSpecValidatorTest : FunSpec({

    fun tempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("schema-validator-test").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    test("valid schema passes without error") {
        tempDir { dir ->
            dir.resolve("valid.schema.json").writeText(
                """
                {
                  "${"$"}schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "properties": {
                    "name": { "type": "string" }
                  }
                }
                """.trimIndent()
            )
            validateJsonApiSpec(dir.absolutePath)
        }
    }

    test("empty directory passes without error") {
        tempDir { dir ->
            validateJsonApiSpec(dir.absolutePath)
        }
    }

    test("invalid JSON fails the build") {
        tempDir { dir ->
            dir.resolve("broken.schema.json").writeText("{ not valid json }")
            val ex = shouldThrow<IllegalStateException> {
                validateJsonApiSpec(dir.absolutePath)
            }
            ex.message shouldBe "JSON API schema validation failed"
        }
    }

    test("schema with unresolvable ref fails the build") {
        tempDir { dir ->
            dir.resolve("bad-ref.schema.json").writeText(
                """
                {
                  "${"$"}schema": "https://json-schema.org/draft/2020-12/schema",
                  "${"$"}ref": "nonexistent.schema.json#/${"$"}defs/something"
                }
                """.trimIndent()
            )
            shouldThrow<IllegalStateException> {
                validateJsonApiSpec(dir.absolutePath)
            }
        }
    }

    test("cross-referencing schemas in the same directory resolve correctly") {
        tempDir { dir ->
            dir.resolve("base.schema.json").writeText(
                """
                {
                  "${"$"}defs": {
                    "name": { "type": "string" }
                  }
                }
                """.trimIndent()
            )
            dir.resolve("referencing.schema.json").writeText(
                """
                {
                  "${"$"}schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "properties": {
                    "name": { "${"$"}ref": "base.schema.json#/${"$"}defs/name" }
                  }
                }
                """.trimIndent()
            )
            validateJsonApiSpec(dir.absolutePath)
        }
    }

    test("multiple invalid schemas all report errors and fail the build") {
        tempDir { dir ->
            dir.resolve("broken-1.schema.json").writeText("{ invalid }")
            dir.resolve("broken-2.schema.json").writeText("[ also invalid")
            val output = StringBuilder()
            val ex = shouldThrow<IllegalStateException> {
                validateJsonApiSpec(dir.absolutePath)
            }
            ex.message shouldBe "JSON API schema validation failed"
        }
    }
})
