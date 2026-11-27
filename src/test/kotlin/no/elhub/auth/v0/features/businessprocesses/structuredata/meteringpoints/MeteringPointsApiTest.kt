package no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
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
import io.ktor.http.fullPath
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

class MeteringPointsApiTest : FunSpec({

    val validMeteringPointId = "300362000000000008"
    val secondMeteringPointId = "300362000000000009"
    val nonExistingMeteringPointId = "300362000000000000"
    lateinit var traceId: String
    var capturedTraceId: String? = null
    val serviceUrl = "http://localhost:8080"
    val config = MeteringPointsApiConfig(
        serviceUrl = serviceUrl,
    )
    val mockJwtTokenProvider = mockk<JwtTokenProvider>()
    coEvery { mockJwtTokenProvider.getToken() } returns "token"

    val client = HttpClient(MockEngine) {
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                }
            )
        }

        engine {
            addHandler { request ->
                capturedTraceId = request.headers[ELHUB_TRACE_ID_HEADER]
                when (request.url.fullPath) {
                    "/metering-points/$validMeteringPointId" -> {
                        respond(
                            content = """
                                {
                                    "data":{
                                        "id":"$validMeteringPointId",
                                        "type":"metering-point",
                                        "relationships":{}}
                                    }
                            """.trimMargin(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    "/metering-points/$secondMeteringPointId" -> {
                        respond(
                            content = """{"data":{"id":"$secondMeteringPointId","type":"metering-point","relationships":{}}}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    else -> respond(
                        content = """{"errors":[{"status":"404","title":"MeteringPoint not found","detail":"No metering point found"}]}""",
                        status = HttpStatusCode.NotFound
                    )
                }
            }
        }
    }
    val service = MeteringPointsApi(config, client, mockJwtTokenProvider)

    beforeTest {
        traceId = UUID.randomUUID().toString()
        capturedTraceId = null
        MDC.put(TRACE_ID_MDC_KEY, traceId)
    }

    afterTest {
        MDC.remove(TRACE_ID_MDC_KEY)
    }

    test("Includes trace id header from MDC in outgoing request") {
        service.getMeteringPointById(validMeteringPointId).shouldBeRight()

        capturedTraceId shouldBe traceId
    }

    test("Missing trace id in MDC does not fail request") {
        MDC.remove(TRACE_ID_MDC_KEY)

        val response = service.getMeteringPointById(validMeteringPointId)

        response.shouldBeRight()
        capturedTraceId.shouldBeNull()
    }

    test("Valid metering point id") {
        val response = service.getMeteringPointById(validMeteringPointId)
        response.shouldBeRight()
        response.value.data.id shouldBe validMeteringPointId
    }

    test("Non existing metering point") {
        val response = service.getMeteringPointById(nonExistingMeteringPointId)
        response.shouldBeLeft()
        response.value shouldBe ClientError.NotFound
    }

    test("Fetches multiple metering points") {
        val response = service.getMeteringPointsByIds(listOf(secondMeteringPointId, validMeteringPointId))

        response.map { it.data.id } shouldBe listOf(secondMeteringPointId, validMeteringPointId)
    }

    test("Throws not found when any requested metering point does not exist") {
        val error = shouldThrow<MeteringPointsLookupException> {
            service.getMeteringPointsByIds(listOf(validMeteringPointId, nonExistingMeteringPointId))
        }

        error.clientError shouldBe ClientError.NotFound
    }
})
