package no.elhub.auth.features.businessprocesses.datasharing

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
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
import no.elhub.auth.features.businessprocesses.structuredata.common.ClientError

class StromprisApiTest : FunSpec({
    val validOrganizationNumber = "123456789"
    val organizationNumberWithNoData = "987654321"
    val notValidValidOrganizationNumber = "1234"
    val mockJwtTokenProvider = mockk<JwtTokenProvider>()
    coEvery { mockJwtTokenProvider.getToken() } returns "token"

    val stromprisApiConfig = StromprisApiConfig(
        serviceUrl = "http://localhost:8080/strompris/v1",
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
                    "/strompris/v1/products?organizationNumber=$validOrganizationNumber" -> {
                        respond(
                            content = """
                                {"data":[
                                    {"type": "Product", "id": "24033","attributes":
                                        {"id": 24033, "name": "Hjemkraft Spot"}
                                    },
                                    {"type": "Product","id": "24034", "attributes":
                                        {"id": 24034, "name": "Hjemkraft Fast 12m"}
                                    }
                                ]}
                            """.trimMargin(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    "/strompris/v1/products?organizationNumber=$organizationNumberWithNoData" -> {
                        respond(
                            content = """
                                {"errors":[{
                                    "status": "404",
                                    "code": "NOT_FOUND",
                                    "title": "Not Found",
                                    "detail": "The requested resource could not be found"
                                }]}
                            """.trimMargin(),
                            status = HttpStatusCode.NotFound
                        )
                    }

                    else -> respond(
                        content = """
                            {"errors":[{
                                "status": "400",
                                "code": "INVALID_INPUT",
                                "title": "Invalid input",
                                "detail": "The provided input did not satisfy the expected format"
                            }]}
                        """.trimMargin(),
                        status = HttpStatusCode.BadRequest
                    )
                }
            }
        }
    }
    val service = StromprisApi(
        stromprisApiConfig = stromprisApiConfig,
        client = client,
        tokenProvider = mockJwtTokenProvider
    )

    test("valid organization number returns products") {
        val response = service.getProductsByOrganizationNumber(validOrganizationNumber)
        response.shouldBeRight()
        response.value.data.size shouldBe 2
    }

    test("organization number that does not have data returns not found error") {
        val response = service.getProductsByOrganizationNumber(organizationNumberWithNoData)
        response.shouldBeLeft()
        response.value shouldBe ClientError.NotFound
    }

    test("not valid organization number returns bad request error") {
        val response = service.getProductsByOrganizationNumber(notValidValidOrganizationNumber)
        response.shouldBeLeft()
        response.value shouldBe ClientError.BadRequest
    }
})
