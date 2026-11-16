package no.elhub.auth.common.config

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
import no.elhub.auth.common.ELHUB_TRACE_ID_HEADER
import no.elhub.auth.common.V1_ELHUB_TRACE_ID_HEADER
import no.elhub.auth.common.configureErrorHandling
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

class TracingTest : FunSpec({
    listOf("v0" to ELHUB_TRACE_ID_HEADER, "v1" to V1_ELHUB_TRACE_ID_HEADER).forEach { (version, header) ->
        test("uses $header header as call id for $version") {
            val traceId = "850cf459-d425-409a-a05d-7c6c9d1c0d64"

            testApplication {
                application {
                    configureRequestTracing()
                    routing {
                        get("/access/$version/call-id") {
                            call.respondText(call.callId.orEmpty())
                        }
                    }
                }

                val response = client.get("/access/$version/call-id") {
                    headers.append(header, traceId)
                }

                response.bodyAsText() shouldBe traceId
                response.headers[header] shouldBe traceId
                response.headers[if (version == "v0") V1_ELHUB_TRACE_ID_HEADER else ELHUB_TRACE_ID_HEADER].shouldBeNull()
            }
        }

        test("generates call id when $header header is missing for $version") {
            testApplication {
                application {
                    configureRequestTracing()
                    routing {
                        get("/access/$version/call-id") {
                            call.respondText(call.callId.orEmpty())
                        }
                    }
                }

                val response = client.get("/access/$version/call-id")
                val generatedTraceId = response.bodyAsText()

                UUID.fromString(generatedTraceId).toString() shouldBe generatedTraceId
                response.headers[header] shouldBe generatedTraceId
                response.headers[if (version == "v0") V1_ELHUB_TRACE_ID_HEADER else ELHUB_TRACE_ID_HEADER].shouldBeNull()
            }
        }

        test("accepts the other header for $version and replies with $header") {
            testApplication {
                application {
                    configureRequestTracing()
                    routing { get("/access/$version/call-id") { call.respondText(call.callId.orEmpty()) } }
                }
                val traceId = UUID.randomUUID().toString()
                val otherHeader = if (version == "v0") V1_ELHUB_TRACE_ID_HEADER else ELHUB_TRACE_ID_HEADER
                val response = client.get("/access/$version/call-id") {
                    headers.append(otherHeader, traceId)
                }
                response.bodyAsText() shouldBe traceId
                response.headers[header] shouldBe traceId
                response.headers[otherHeader].shouldBeNull()
            }
        }

        test("prefers the hyphenated header when both headers are present for $version") {
            testApplication {
                application {
                    configureRequestTracing()
                    routing { get("/access/$version/call-id") { call.respondText(call.callId.orEmpty()) } }
                }
                val traceId = UUID.randomUUID().toString()
                val otherHeader = if (version == "v0") V1_ELHUB_TRACE_ID_HEADER else ELHUB_TRACE_ID_HEADER
                val response = client.get("/access/$version/call-id") {
                    headers.append(V1_ELHUB_TRACE_ID_HEADER, traceId)
                    headers.append(ELHUB_TRACE_ID_HEADER, "not-a-uuid")
                }
                response.bodyAsText() shouldBe traceId
                response.headers[header] shouldBe traceId
                response.headers[otherHeader].shouldBeNull()
            }
        }

        context("returns 400 when $header header is invalid for $version") {
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
                                get("/access/$version/call-id") {
                                    call.respondText(call.callId.orEmpty())
                                }
                            }
                        }

                        val jsonClient = createClient {
                            install(ClientContentNegotiation) { json() }
                        }

                        val response = jsonClient.get("/access/$version/call-id") {
                            headers.append(header, invalidValue)
                        }

                        response.status shouldBe HttpStatusCode.BadRequest
                        response.headers[header].shouldBeNull()
                        val responseJson: JsonApiErrorCollection = response.body()
                        responseJson.errors.apply {
                            size shouldBe 1
                            this[0].apply {
                                status shouldBe "400"
                                title shouldBe "Invalid trace ID"
                                detail shouldBe "Elhub-Trace-Id header must be a valid UUID"
                            }
                        }
                    }
                }
            }
        }
    }

    test("v0 rejects an invalid hyphenated trace header even when the legacy header is valid") {
        testApplication {
            application {
                configureSerialization()
                configureErrorHandling()
                configureRequestTracing()
                routing { get("/access/v0/call-id") { call.respondText(call.callId.orEmpty()) } }
            }
            val response = client.get("/access/v0/call-id") {
                headers.append(V1_ELHUB_TRACE_ID_HEADER, "not-a-uuid")
                headers.append(ELHUB_TRACE_ID_HEADER, UUID.randomUUID().toString())
            }
            response.status shouldBe HttpStatusCode.BadRequest
            response.headers[ELHUB_TRACE_ID_HEADER].shouldBeNull()
        }
    }

    test("v1 rejects an invalid hyphenated trace header even when the legacy header is valid") {
        testApplication {
            application {
                configureSerialization()
                configureErrorHandling()
                configureRequestTracing()
                routing { get("/access/v1/call-id") { call.respondText(call.callId.orEmpty()) } }
            }
            val response = client.get("/access/v1/call-id") {
                headers.append(V1_ELHUB_TRACE_ID_HEADER, "not-a-uuid")
                headers.append(ELHUB_TRACE_ID_HEADER, UUID.randomUUID().toString())
            }
            response.status shouldBe HttpStatusCode.BadRequest
            response.headers[V1_ELHUB_TRACE_ID_HEADER].shouldBeNull()
        }
    }

    test("v0 rejects an invalid hyphenated trace header") {
        testApplication {
            application {
                configureSerialization()
                configureErrorHandling()
                configureRequestTracing()
                routing { get("/access/v0/call-id") { call.respondText(call.callId.orEmpty()) } }
            }
            val jsonClient = createClient { install(ClientContentNegotiation) { json() } }
            val response = jsonClient.get("/access/v0/call-id") {
                headers.append(V1_ELHUB_TRACE_ID_HEADER, "not-a-uuid")
            }
            response.status shouldBe HttpStatusCode.BadRequest
            response.headers[ELHUB_TRACE_ID_HEADER].shouldBeNull()
            val body: JsonApiErrorCollection = response.body()
            body.errors[0].detail shouldBe "Elhub-Trace-Id header must be a valid UUID"
        }
    }

    test("v1 rejects an invalid legacy trace header") {
        testApplication {
            application {
                configureSerialization()
                configureErrorHandling()
                configureRequestTracing()
                routing { get("/access/v1/call-id") { call.respondText(call.callId.orEmpty()) } }
            }
            val jsonClient = createClient { install(ClientContentNegotiation) { json() } }
            val response = jsonClient.get("/access/v1/call-id") {
                headers.append(ELHUB_TRACE_ID_HEADER, "not-a-uuid")
            }
            response.status shouldBe HttpStatusCode.BadRequest
            response.headers[V1_ELHUB_TRACE_ID_HEADER].shouldBeNull()
            val body: JsonApiErrorCollection = response.body()
            body.errors[0].detail shouldBe "Elhub-Trace-Id header must be a valid UUID"
        }
    }
})
