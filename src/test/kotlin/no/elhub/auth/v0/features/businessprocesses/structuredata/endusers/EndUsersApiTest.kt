package no.elhub.auth.v0.features.businessprocesses.structuredata.endusers

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import no.elhub.auth.common.ELHUB_TRACE_ID_HEADER
import no.elhub.auth.common.TRACE_ID_MDC_KEY
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProvider
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
import org.slf4j.MDC
import java.util.UUID

class EndUsersApiTest : FunSpec({
    val meteringPointId = "300362000000000008"
    val anotherMeteringPointId = "300362000000000009"
    val endUserId = "d6784082-8344-e733-e053-02058d0a6752"
    val traceId = UUID.randomUUID().toString()
    var capturedMeteringPointId: String? = null
    var capturedAuthorizationHeader: String? = null
    var capturedTraceIdHeader: String? = null
    val serviceUrl = "http://localhost:8080"
    val config = EndUsersApiConfig(serviceUrl)
    val tokenProvider = mockk<JwtTokenProvider>()
    coEvery { tokenProvider.getToken() } returns "token"

    val client = HttpClient(MockEngine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }

        engine {
            addHandler { request ->
                capturedMeteringPointId = request.url.parameters["meteringPointId"]
                capturedAuthorizationHeader = request.headers[HttpHeaders.Authorization]
                capturedTraceIdHeader = request.headers[ELHUB_TRACE_ID_HEADER]
                if (capturedMeteringPointId == meteringPointId) {
                    respond(
                        content = """
                            {
                                "data": {
                                    "id": "$endUserId",
                                    "type": "end-user",
                                    "attributes": {
                                        "endUserUniqueId": "$endUserId",
                                        "endUserId": "$endUserId",
                                        "endUserType": "Person"
                                    }
                                }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                } else {
                    respond(
                        content = """{"errors":[{"status":"404","title":"Not Found","detail":"End user not found"}]}""",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }
    }
    val service = EndUsersApi(config, client, tokenProvider)

    beforeTest {
        capturedMeteringPointId = null
        capturedAuthorizationHeader = null
        capturedTraceIdHeader = null
        MDC.put(TRACE_ID_MDC_KEY, traceId)
    }

    afterTest {
        MDC.remove(TRACE_ID_MDC_KEY)
    }

    test("queries an end user by metering point ID") {
        val response = service.getEndUserByMeteringPointId(meteringPointId)

        response.shouldBeRight()
        capturedMeteringPointId shouldBe meteringPointId
        capturedAuthorizationHeader shouldBe "Bearer token"
        capturedTraceIdHeader shouldBe traceId
        response.value.data.attributes?.endUserUniqueId shouldBe endUserId
    }

    test("maps an unknown end user to not found") {
        val response = service.getEndUserByMeteringPointId(anotherMeteringPointId)

        response.shouldBeLeft()
        response.value shouldBe ClientError.NotFound
    }

    test("omits the trace header when no trace ID is available") {
        MDC.remove(TRACE_ID_MDC_KEY)

        service.getEndUserByMeteringPointId(meteringPointId).shouldBeRight()

        capturedTraceIdHeader.shouldBeNull()
    }
})
