package no.elhub.auth.common.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.elhub.auth.common.ApiHeaders

class GlnHeaderTest : FunSpec({
    listOf(
        ApiHeaders.ELHUB_SENDER_GLN to ApiHeaders.SENDER_GLN,
        ApiHeaders.ELHUB_ON_BEHALF_OF_GLN to ApiHeaders.ON_BEHALF_OF_GLN,
    ).forEach { (preferred, legacy) ->
        suspend fun io.ktor.server.testing.ApplicationTestBuilder.resolvedGln(
            path: String,
            preferredValue: String? = null,
            legacyValue: String? = null,
        ): String = client.get(path) {
            preferredValue?.let { header(preferred, it) }
            legacyValue?.let { header(legacy, it) }
        }.bodyAsText()

        test("v1 prefers $preferred and falls back to $legacy") {
            testApplication {
                application {
                    install(ResolveAuthorizationHeaders)
                    routing {
                        get("/access/v1/authorization-documents") {
                            val values = call.authorizationHeaders
                            call.respondText(
                                if (preferred == ApiHeaders.ELHUB_SENDER_GLN) {
                                    values.senderGln ?: "missing"
                                } else {
                                    values.onBehalfOfGln ?: "missing"
                                },
                            )
                        }
                    }
                }

                resolvedGln("/access/v1/authorization-documents", preferredValue = "123") shouldBe "123"
                resolvedGln("/access/v1/authorization-documents", legacyValue = "456") shouldBe "456"
                resolvedGln("/access/v1/authorization-documents", preferredValue = "123", legacyValue = "456") shouldBe "123"
                resolvedGln("/access/v1/authorization-documents", preferredValue = "", legacyValue = "456") shouldBe "456"
            }
        }

        test("v0 ignores $preferred and reads only $legacy") {
            testApplication {
                application {
                    install(ResolveAuthorizationHeaders)
                    routing {
                        get("/access/v0/authorization-documents") {
                            val values = call.authorizationHeaders
                            call.respondText(
                                if (preferred == ApiHeaders.ELHUB_SENDER_GLN) {
                                    values.senderGln ?: "missing"
                                } else {
                                    values.onBehalfOfGln ?: "missing"
                                },
                            )
                        }
                    }
                }

                resolvedGln("/access/v0/authorization-documents", preferredValue = "123", legacyValue = "456") shouldBe "456"
                resolvedGln("/access/v0/authorization-documents", preferredValue = "123") shouldBe "missing"
            }
        }
    }
})
