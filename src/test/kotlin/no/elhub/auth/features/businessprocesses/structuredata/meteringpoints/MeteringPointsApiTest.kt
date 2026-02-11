package no.elhub.auth.features.businessprocesses.structuredata.meteringpoints

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
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
    val otherEndUserId = "00662e04-2fd6-3b06-b672-3965abe7b7c5"
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
                            content = """
                                {
                                    "data":{
                                        "id":"$validMeteringPointId",
                                        "type":"metering-point",
                                        "attributes":{"accountingPoint":{},"accessType": "OWNED"},
                                        "relationships":{"endUser":{"data":{"id":"end-user-unique-id","type":"end-user"}}}}
                                    }
                            """.trimMargin(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    "/service/metering-points/$validMeteringPointId?endUserId=$otherEndUserId" -> {
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
    val service = MeteringPointsApi(config, client)

    test("Valid metering point id and end user id") {
        val response = service.getMeteringPointByIdAndElhubInternalId(validMeteringPointId, endUserId)
        response.shouldBeRight()
        response.value.data.id shouldBe validMeteringPointId
        response.value.data.relationships.endUser.shouldNotBeNull()
    }

    test("Valid metering point id and not corresponding end user id") {
        val response = service.getMeteringPointByIdAndElhubInternalId(validMeteringPointId, otherEndUserId)
        response.shouldBeRight()
        response.value.data.id shouldBe validMeteringPointId
        response.value.data.relationships.endUser.shouldBeNull()
    }

    test("Non existing metering point") {
        val response = service.getMeteringPointByIdAndElhubInternalId("300362000000000000", "some-end-user-id")
        response.shouldBeLeft()
        response.value.toString() shouldContain "MeteringPoint not found"
    }
})
