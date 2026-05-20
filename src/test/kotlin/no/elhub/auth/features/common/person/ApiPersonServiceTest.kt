package no.elhub.auth.features.common.person

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import no.elhub.auth.features.businessprocesses.common.JwtTokenProvider
import no.elhub.auth.features.common.ELHUB_TRACE_ID_HEADER
import no.elhub.auth.features.common.TRACE_ID_MDC_KEY
import org.slf4j.MDC
import java.util.UUID

class ApiPersonServiceTest : FunSpec({
    val nin = "12345678910"
    val traceId = UUID.randomUUID().toString()
    var capturedTraceIdHeader: String? = null
    val tokenProvider = mockk<JwtTokenProvider>()
    coEvery { tokenProvider.getToken() } returns "token"

    val client = HttpClient(MockEngine) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                }
            )
        }

        engine {
            addHandler { request ->
                capturedTraceIdHeader = request.headers[ELHUB_TRACE_ID_HEADER]
                when (request.url.fullPath) {
                    "/market-parties/v0/persons" -> respond(
                        content = """
                            {
                              "data": {
                                "type": "Person",
                                "id": "11111111-1111-1111-1111-111111111111",
                                "attributes": {
                                  "type": "Person",
                                  "id": "11111111-1111-1111-1111-111111111111"
                                }
                              }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )

                    else -> respond("{}", HttpStatusCode.NotFound)
                }
            }
        }
    }

    val service = ApiPersonService(
        cfg = PersonApiConfig(baseUri = "http://localhost"),
        client = client,
        tokenProvider = tokenProvider
    )

    beforeTest {
        capturedTraceIdHeader = null
        MDC.put(TRACE_ID_MDC_KEY, traceId)
    }

    afterTest {
        MDC.remove(TRACE_ID_MDC_KEY)
    }

    test("sends auth persons trace header from shared trace context") {
        service.findOrCreateByNin(nin).shouldBeRight()

        capturedTraceIdHeader shouldBe traceId
    }

    test("returns missing header when trace id is absent") {
        MDC.remove(TRACE_ID_MDC_KEY)

        service.findOrCreateByNin(nin).shouldBeLeft(ClientError.MissingHeader)
    }
})
