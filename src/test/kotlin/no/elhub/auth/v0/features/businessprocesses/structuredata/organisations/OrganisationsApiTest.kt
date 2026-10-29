package no.elhub.auth.v0.features.businessprocesses.structuredata.organisations

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
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
import no.elhub.auth.common.structuredata.organisations.PartyType
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProvider
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.v0.features.common.ELHUB_TRACE_ID_HEADER
import no.elhub.auth.v0.features.common.TRACE_ID_MDC_KEY
import org.slf4j.MDC
import java.util.UUID

class OrganisationsApiTest : FunSpec({
    val validPartyId = "3004300000019"
    val notBalanceSupplierPartyId = "3004300000099"
    val notValidPartyId = "123"
    val elhubInternalId = "e95672ef-3066-2cb6-e053-04058d0ab192"
    val elhubInternalIdWithoutOrganisations = "e95672ef-3067-2cb6-e053-04058d0ab192"
    val traceId = UUID.randomUUID().toString()
    var capturedTraceIdHeader: String? = null
    var capturedAuthorizationHeader: String? = null
    val serviceUrl = "http://localhost:8080/v1"
    val config = OrganisationsApiConfig(
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
                capturedTraceIdHeader = request.headers[ELHUB_TRACE_ID_HEADER]
                capturedAuthorizationHeader = request.headers[HttpHeaders.Authorization]
                when (request.url.fullPath) {
                    "/v1/parties/$validPartyId?partyType=BalanceSupplier" -> {
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

                    "/v1/parties/$notBalanceSupplierPartyId?partyType=BalanceSupplier" -> {
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

                    "/v1/parties/$notValidPartyId?partyType=BalanceSupplier" -> {
                        respond(
                            content = """{"errors":[{"status":"400","title":"Invalid partyId value","detail":"Invalid value for partyId"}]}""",
                            status = HttpStatusCode.BadRequest,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    "/v1/altinn?elhubInternalId=$elhubInternalId" -> {
                        respond(
                            content = """
                                {
                                    "data": [
                                        {
                                            "id": "$elhubInternalId",
                                            "type": "organization",
                                            "attributes": {
                                                "organizationNo": "721433048",
                                                "name": "Organization_01"
                                            }
                                        }
                                    ]
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    "/v1/altinn?elhubInternalId=$elhubInternalIdWithoutOrganisations" -> {
                        respond(
                            content = """{"data":[]}""",
                            status = HttpStatusCode.OK,
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
    val service = OrganisationsApi(config, client, mockJwtTokenProvider)

    beforeTest {
        capturedTraceIdHeader = null
        capturedAuthorizationHeader = null
        MDC.put(TRACE_ID_MDC_KEY, traceId)
    }

    afterTest {
        MDC.remove(TRACE_ID_MDC_KEY)
    }

    test("sends trace header from shared trace context") {
        service.getPartyByIdAndPartyType(validPartyId, PartyType.BalanceSupplier).shouldBeRight()

        capturedTraceIdHeader shouldBe traceId
    }

    test("sends bearer token in authorization header") {
        service.getPartyByIdAndPartyType(validPartyId, PartyType.BalanceSupplier).shouldBeRight()

        capturedAuthorizationHeader shouldBe "Bearer token"
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

    test("returns Altinn organisations for an Elhub internal id") {
        val response = service.getAltinnOrganisationsByElhubInternalId(elhubInternalId)

        response.shouldBeRight()
        response.value.data.map { it.attributes?.organizationNo } shouldBe listOf("721433048")
        response.value.data.first().attributes.shouldNotBeNull().name shouldBe "Organization_01"
    }

    test("returns an empty collection when no Altinn organisations exist") {
        val response = service.getAltinnOrganisationsByElhubInternalId(elhubInternalIdWithoutOrganisations)

        response.shouldBeRight()
        response.value.data shouldBe emptyList()
    }
})
