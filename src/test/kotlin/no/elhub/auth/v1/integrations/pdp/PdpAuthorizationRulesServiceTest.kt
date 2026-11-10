package no.elhub.auth.v1.integrations.pdp

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import no.elhub.auth.common.TRACE_ID_MDC_KEY
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.v0.features.businessprocesses.common.JwtTokenProvider
import org.slf4j.MDC

class PdpAuthorizationRulesServiceTest : FunSpec({
    val person = AuthorizationParty("11111111-1111-4111-8111-111111111111", PartyType.Person)
    val organization = AuthorizationParty("111111111", PartyType.Organization)
    lateinit var tokenProvider: JwtTokenProvider
    lateinit var client: HttpClient
    lateinit var service: PdpAuthorizationRulesService
    var responseBody = ""
    var responseStatus = HttpStatusCode.OK
    var requestCount = 0
    var capturedBody = ""

    beforeTest {
        MDC.put(TRACE_ID_MDC_KEY, "trace-123")
        responseBody = """{"result":{"authorized":true,"tokenInfo":{"tokenStatus":"verified"}}}"""
        responseStatus = HttpStatusCode.OK
        requestCount = 0
        capturedBody = ""
        tokenProvider = mockk()
        coEvery { tokenProvider.getToken() } returns "service-token"
        client = HttpClient(
            MockEngine { request ->
                requestCount++
                request.method shouldBe HttpMethod.Post
                request.url.toString() shouldBe "https://pdp.example/v1/data/v3/token/agent/representative"
                request.body.contentType shouldBe ContentType.Application.Json
                capturedBody = (request.body as TextContent).text
                respond(
                    responseBody,
                    responseStatus,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        )
        service = PdpAuthorizationRulesService(client, "https://pdp.example/", tokenProvider)
    }

    afterTest {
        client.close()
        MDC.remove(TRACE_ID_MDC_KEY)
    }

    test("allows same-party authorization without a trace ID, token or PDP call") {
        MDC.remove(TRACE_ID_MDC_KEY)
        listOf(person, organization).forEach { party ->
            service.canActOnBehalfOf(party, party.copy()) shouldBe true
        }
        coVerify(exactly = 0) { tokenProvider.getToken() }
        requestCount shouldBe 0
    }

    test("delegates unsupported party combinations to PDP") {
        responseBody = """{"result":{"authorized":false,"tokenInfo":{"tokenStatus":"verified"}}}"""
        service.canActOnBehalfOf(
            AuthorizationParty("representative", PartyType.OrganizationEntity),
            AuthorizationParty("principal", PartyType.Person),
        ) shouldBe false

        coVerify(exactly = 1) { tokenProvider.getToken() }
        requestCount shouldBe 1
    }

    test("sends the service token, trace ID and agent identifiers using the policy contract") {
        service.canActOnBehalfOf(person, organization) shouldBe true
        val expectedBody = """
            {
              "input": {
                "token": "service-token",
                "ElhubTraceId": "trace-123",
                "payload": {
                  "RepresentativeId": "11111111-1111-4111-8111-111111111111",
                  "PrincipalId": "111111111"
                }
              }
            }
        """.trimIndent()
        Json.parseToJsonElement(capturedBody) shouldBe Json.parseToJsonElement(
            expectedBody,
        )
        coVerify(exactly = 1) { tokenProvider.getToken() }
        requestCount shouldBe 1
    }

    test("returns false for an explicit policy denial") {
        responseBody = """{"result":{"authorized":false,"tokenInfo":{"tokenStatus":"verified"}}}"""
        service.canActOnBehalfOf(person, organization) shouldBe false
    }

    test("passes matching IDs with different party types to PDP") {
        service.canActOnBehalfOf(person, organization.copy(id = person.id)) shouldBe true
        requestCount shouldBe 1
    }

    test("requires a trace ID before fetching a token or calling PDP") {
        MDC.remove(TRACE_ID_MDC_KEY)
        shouldThrow<IllegalStateException> {
            service.canActOnBehalfOf(person, organization)
        }
        coVerify(exactly = 0) { tokenProvider.getToken() }
        requestCount shouldBe 0
    }

    test("throws for unsuccessful HTTP responses") {
        listOf(HttpStatusCode.Forbidden, HttpStatusCode.ServiceUnavailable).forEach { status ->
            responseStatus = status
            shouldThrow<PdpAuthorizationException> {
                service.canActOnBehalfOf(person, organization)
            }
        }
    }

    test("throws for a PDP error even when authorized is true") {
        responseBody = """{"result":{"authorized":true,"error":{"code":"error","message":"failure"}}}"""
        shouldThrow<PdpAuthorizationException> {
            service.canActOnBehalfOf(person, organization)
        }.message shouldBe "PDP returned an authorization error"
    }

    test("requires verified token information") {
        listOf(
            """{"result":{"authorized":true}}""",
            """{"result":{"authorized":true,"tokenInfo":{"tokenStatus":"invalid"}}}""",
        ).forEach { body ->
            responseBody = body
            shouldThrow<PdpAuthorizationException> {
                service.canActOnBehalfOf(person, organization)
            }
        }
    }

    test("propagates malformed PDP responses") {
        responseBody = """{"result":{}}"""
        shouldThrow<SerializationException> {
            service.canActOnBehalfOf(person, organization)
        }
    }

    test("propagates token provider failures without calling PDP") {
        val failure = IllegalStateException("Token unavailable")
        coEvery { tokenProvider.getToken() } throws failure
        shouldThrow<IllegalStateException> {
            service.canActOnBehalfOf(person, organization)
        } shouldBe failure
        requestCount shouldBe 0
    }

    test("propagates cancellation without calling PDP") {
        coEvery { tokenProvider.getToken() } throws CancellationException("Cancelled")
        shouldThrow<CancellationException> {
            service.canActOnBehalfOf(person, organization)
        }
        requestCount shouldBe 0
    }
})
