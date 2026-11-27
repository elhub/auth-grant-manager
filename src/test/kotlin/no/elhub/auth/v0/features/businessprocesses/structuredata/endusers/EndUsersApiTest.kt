package no.elhub.auth.v0.features.businessprocesses.structuredata.endusers

import io.kotest.assertions.throwables.shouldThrow
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
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import no.elhub.auth.common.ELHUB_TRACE_ID_HEADER
import no.elhub.auth.common.TRACE_ID_MDC_KEY
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProvider
import org.slf4j.MDC
import java.io.IOException
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
    var errorStatus = HttpStatusCode.NotFound

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
                        status = errorStatus,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }
    }
    val service = EndUsersApi(config, client, tokenProvider)

    beforeTest {
        clearMocks(tokenProvider)
        coEvery { tokenProvider.getToken() } returns "token"
        errorStatus = HttpStatusCode.NotFound
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

        capturedMeteringPointId shouldBe meteringPointId
        capturedAuthorizationHeader shouldBe "Bearer token"
        capturedTraceIdHeader shouldBe traceId
        response.data.attributes?.endUserUniqueId shouldBe endUserId
    }

    test("throws when the current end user is not found") {
        shouldThrow<CurrentEndUserNotFoundException> {
            service.getEndUserByMeteringPointId(anotherMeteringPointId)
        }
    }

    listOf(HttpStatusCode.BadRequest, HttpStatusCode.Unauthorized, HttpStatusCode.InternalServerError).forEach { status ->
        test("throws a lookup exception when the end user service returns HTTP ${status.value}") {
            errorStatus = status

            val thrown = shouldThrow<EndUserLookupException> {
                service.getEndUserByMeteringPointId(anotherMeteringPointId)
            }

            thrown.message shouldBe "EndUsersService returned HTTP ${status.value}"
        }
    }

    test("propagates unexpected token provider failures unchanged") {
        val failure = IOException("Token endpoint unavailable")
        coEvery { tokenProvider.getToken() } throws failure

        val thrown = shouldThrow<IOException> {
            service.getEndUserByMeteringPointId(meteringPointId)
        }

        thrown shouldBe failure
    }

    test("propagates cancellation unchanged") {
        val cancellation = CancellationException("Lookup cancelled")
        coEvery { tokenProvider.getToken() } throws cancellation

        val thrown = shouldThrow<CancellationException> {
            service.getEndUserByMeteringPointId(meteringPointId)
        }

        thrown shouldBe cancellation
    }

    test("omits the trace header when no trace ID is available") {
        MDC.remove(TRACE_ID_MDC_KEY)

        service.getEndUserByMeteringPointId(meteringPointId)

        capturedTraceIdHeader.shouldBeNull()
    }
})
