package no.elhub.auth.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

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

    context("returns 400 when ElhubTraceID header is invalid") {
        listOf(
            "not-a-uuid",
            "",
            "   ",
            "123",
            "850cf459-d425-409a-a05d-7c6c9d1c0d64-extra"
        ).forEach { invalidValue ->
            test("rejects '$invalidValue'") {
                testApplication {
                    application {
                        configureSerialization()
                        configureErrorHandling()
                        configureRequestTracing()
                        routing {
                            get("/call-id") {
                                call.respondText(call.callId.orEmpty())
                            }
                        }
                    }

                    val jsonClient = createClient {
                        install(ClientContentNegotiation) { json() }
                    }

                    val response = jsonClient.get("/call-id") {
                        headers.append("ElhubTraceID", invalidValue)
                    }

                    response.status shouldBe HttpStatusCode.BadRequest
                    response.headers["ElhubTraceID"].shouldBeNull()
                    response.headers["Elhub-Trace-Id"].shouldBeNull()
                    val responseJson: JsonApiErrorCollection = response.body()
                    responseJson.errors.apply {
                        size shouldBe 1
                        this[0].apply {
                            status shouldBe "400"
                            title shouldBe "Invalid trace ID"
                            detail shouldBe "Header 'ElhubTraceID' must be a valid UUID"
                        }
                    }
                }
            }
        }
    }
})
