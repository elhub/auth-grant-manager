package no.elhub.auth.features.businessprocesses.structuredata.organisations

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
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.elhub.auth.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.features.common.ELHUB_TRACE_ID_HEADER
import no.elhub.auth.features.common.TRACE_ID_MDC_KEY
import org.slf4j.MDC
import java.util.UUID

class OrganisationsApiTest : FunSpec({
    val validPartyId = "3004300000019"
    val notBalanceSupplierPartyId = "3004300000099"
    val notValidPartyId = "123"
    val traceId = UUID.randomUUID().toString()
    var capturedTraceIdHeader: String? = null
    val serviceUrl = "http://localhost:8080/v1/service"
    val config = OrganisationsApiConfig(
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
                capturedTraceIdHeader = request.headers[ELHUB_TRACE_ID_HEADER]
                when (request.url.fullPath) {
                    "/v1/service/parties/$validPartyId?partyType=BalanceSupplier" -> {
                        respond(
                            content = """
                                {
                                    "data":{
                                        "id":"$validPartyId",
                                        "type":"party",
                                        "attributes":{
                                            "partyType": "BALANCE_SUPPLIER",
                                            "partyId": "$validPartyId",
                                            "name":"BalanceSupplier Østli, Ruud og Moen",
                                            "status": "ACTIVE"
                                        },
                                        "relationships":{"organizationNumber":{"data":{"id":"455261619","type":"organization"}}}}
                                    }
                            """.trimMargin(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    "/v1/service/parties/$notBalanceSupplierPartyId?partyType=BalanceSupplier" -> {
                        respond(
                            content = """
                                {"errors": [{
                                    "status": "404",
                                    "title": "Party not found",
                                    "detail": "Party not found for id: $notBalanceSupplierPartyId and party type: BalanceSupplier"
                                }]}
                            """.trimMargin(),
                            status = HttpStatusCode.NotFound,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    "/v1/service/parties/$notValidPartyId?partyType=BalanceSupplier" -> {
                        respond(
                            content = """{"errors":[{"status":"400","title":"Invalid partyId value","detail":"Invalid value for partyId"}]}""",
                            status = HttpStatusCode.BadRequest,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    else -> respond(
                        content = """{"errors":[{"status":"401","title":"Unauthorized","detail":"Missing or invalid token"}]}""",
                        status = HttpStatusCode.Unauthorized
                    )
                }
            }
        }
    }
    val service = OrganisationsApi(config, client)

    beforeTest {
        capturedTraceIdHeader = null
        MDC.put(TRACE_ID_MDC_KEY, traceId)
    }

    afterTest {
        MDC.remove(TRACE_ID_MDC_KEY)
    }

    test("sends trace header from shared trace context") {
        service.getPartyByIdAndPartyType(validPartyId, PartyType.BalanceSupplier).shouldBeRight()

        capturedTraceIdHeader shouldBe traceId
    }

    test("missing trace id does not fail request") {
        MDC.remove(TRACE_ID_MDC_KEY)

        val response = service.getPartyByIdAndPartyType(validPartyId, PartyType.BalanceSupplier)

        response.shouldBeRight()
        capturedTraceIdHeader.shouldBeNull()
    }

    test("Valid partyId and corresponding partyType") {
        val response = service.getPartyByIdAndPartyType(validPartyId, PartyType.BalanceSupplier)
        response.shouldBeRight()
        response.value.data.id shouldBe validPartyId
    }

    test("Valid partyId and not corresponding partyType") {
        val response = service.getPartyByIdAndPartyType(notBalanceSupplierPartyId, PartyType.BalanceSupplier)
        response.shouldBeLeft()
        response.value shouldBe ClientError.NotFound
    }

    test("Not valid partyId value") {
        val response = service.getPartyByIdAndPartyType(notValidPartyId, PartyType.BalanceSupplier)
        response.shouldBeLeft()
        response.value shouldBe ClientError.BadRequest
    }
})
