package no.elhub.auth.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.call
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.util.UUID

class TracingTest : FunSpec({
    test("uses ElhubTraceID header as call id when present") {
        val traceId = "850cf459-d425-409a-a05d-7c6c9d1c0d64"

        testApplication {
            application {
                configureRequestTracing()
                routing {
                    get("/call-id") {
                        call.respondText(call.callId.orEmpty())
                    }
                }
            }

            val response = client.get("/call-id") {
                headers.append("ElhubTraceID", traceId)
            }

            response.bodyAsText() shouldBe traceId
            response.headers["ElhubTraceID"] shouldBe traceId
            response.headers["Elhub-Trace-Id"] shouldBe traceId
        }
    }

    test("generates call id when ElhubTraceID header is missing") {
        testApplication {
            application {
                configureRequestTracing()
                routing {
                    get("/call-id") {
                        call.respondText(call.callId.orEmpty())
                    }
                }
            }

            val response = client.get("/call-id")
            val generatedTraceId = response.bodyAsText()

            UUID.fromString(generatedTraceId).toString() shouldBe generatedTraceId
            response.headers["ElhubTraceID"] shouldBe generatedTraceId
            response.headers["Elhub-Trace-Id"] shouldBe generatedTraceId
        }
    }
})
