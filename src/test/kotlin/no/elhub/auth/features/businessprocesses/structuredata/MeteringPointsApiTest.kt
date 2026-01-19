package no.elhub.auth.features.businessprocesses.structuredata

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class MeteringPointsApiTest : FunSpec({

    val validMeteringPointId = "300362000000000008"
    val endUserId = "d6784082-8344-e733-e053-02058d0a6752"
    val serviceUrl = "http://localhost:8080/service"
    val config = MeteringPointsApiConfig(
        serviceUrl = serviceUrl,
        basicAuthConfig = BasicAuthConfig(
            username = "username",
            password = "password"
        )
    )

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
                when (request.url.fullPath) {
                    "/service/metering-points/$validMeteringPointId?endUserId=$endUserId" -> {
                        respond(
                            content = """{"data":{"id":"$validMeteringPointId","type":"metering-point","relationships":{}}}""".trimMargin(),
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

    test("Valid metering point") {
        val service = MeteringPointsApi(config, client)
        val response = service.getMeteringPointByIdAndElhubInternalId(validMeteringPointId, endUserId)
        response.shouldBeRight()
        response.value.data.id shouldBe validMeteringPointId
    }

    test("Invalid metering point") {
        val service = MeteringPointsApi(config, client)
        val response = service.getMeteringPointByIdAndElhubInternalId("invalid-id", "some-end-user-id")
        response.shouldBeLeft()
        response.value.toString() shouldContain "No metering point found"
    }
})
