package no.elhub.auth.common.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.headersOf
import no.elhub.auth.common.ApiHeaders
import no.elhub.auth.common.ConflictingGlnHeadersException

class GlnHeaderTest : FunSpec({
    listOf(
        ApiHeaders.ELHUB_SENDER_GLN to ApiHeaders.SENDER_GLN,
        ApiHeaders.ELHUB_ON_BEHALF_OF_GLN to ApiHeaders.ON_BEHALF_OF_GLN,
    ).forEach { (preferred, legacy) ->
        test("v1 accepts preferred $preferred alone") {
            resolveGlnHeader(headersOf(preferred, "123"), "/access/v1/authorization-documents", preferred, legacy) shouldBe "123"
        }

        test("v1 accepts legacy $legacy alone") {
            resolveGlnHeader(headersOf(legacy, "123"), "/access/v1/authorization-documents", preferred, legacy) shouldBe "123"
        }

        test("v1 accepts matching $preferred and $legacy") {
            val headers = headersOf(preferred to listOf("123"), legacy to listOf("123"))
            resolveGlnHeader(headers, "/access/v1/authorization-documents", preferred, legacy) shouldBe "123"
        }

        test("v1 rejects conflicting $preferred and $legacy") {
            val headers = headersOf(preferred to listOf("123"), legacy to listOf("456"))
            shouldThrow<ConflictingGlnHeadersException> {
                resolveGlnHeader(headers, "/access/v1/authorization-documents", preferred, legacy)
            }
        }

        test("v0 ignores $preferred and retains $legacy") {
            val headers = headersOf(preferred to listOf("123"), legacy to listOf("456"))
            resolveGlnHeader(headers, "/access/v0/authorization-documents", preferred, legacy) shouldBe "456"
            resolveGlnHeader(headersOf(preferred, "123"), "/access/v0/authorization-documents", preferred, legacy) shouldBe null
        }
    }
})
