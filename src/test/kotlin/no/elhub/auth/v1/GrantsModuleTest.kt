package no.elhub.auth.v1

import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.elhub.auth.common.ApiHeaders
import no.elhub.auth.common.auth.AUTHINFO_POLICY_ROUTE
import no.elhub.auth.common.config.configureAuthorization
import no.elhub.auth.common.config.configureRequestTracing
import no.elhub.auth.common.config.configureSerialization
import no.elhub.auth.common.config.jsonApiContentType
import no.elhub.auth.common.configureErrorHandling
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationGrant
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.repositories.GrantRepository
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Instant

class GrantsModuleTest : FunSpec({
    val delegatedAuthInfo = """{"actingId":"organisation-1","actingType":"organisation","originalId":"person-1"}"""

    test("PATCH system can exhaust a grant") {
        GrantPatchFixture(tokenType = "elhub-service", authInfo = "null").use { fixture ->
            val response = fixture.patch(AuthorizationGrantStatus.Exhausted)

            response.status shouldBe HttpStatusCode.OK
            response.assertGrant(fixture.grant.id, AuthorizationGrantStatus.Exhausted)
            fixture.pdpInput().getValue("token").jsonPrimitive.content shouldBe "test-token"
            coVerify(exactly = 1) { fixture.repository.find(fixture.grant.id) }
            coVerify(exactly = 1) { fixture.repository.update(fixture.grant.id, AuthorizationGrantStatus.Exhausted) }
        }
    }

    test("PATCH delegated ENDUSER can revoke as the originalId person matching grantedBy") {
        GrantPatchFixture(tokenType = "enduser", authInfo = delegatedAuthInfo).use { fixture ->
            val response = fixture.patch(AuthorizationGrantStatus.Revoked, onBehalfOfOrganisation = "999888771")

            response.status shouldBe HttpStatusCode.OK
            response.assertGrant(fixture.grant.id, AuthorizationGrantStatus.Revoked)
            val input = fixture.pdpInput()
            input.getValue("token").jsonPrimitive.content shouldBe "test-token"
            input.getValue("payload").jsonObject.getValue("OnBehalfOfOrganisationId").jsonPrimitive.content shouldBe "999888771"
            coVerify(exactly = 1) { fixture.repository.find(fixture.grant.id) }
            coVerify(exactly = 1) { fixture.repository.update(fixture.grant.id, AuthorizationGrantStatus.Revoked) }
        }
    }

    test("PATCH delegated ENDUSER cannot exhaust a grant") {
        GrantPatchFixture(tokenType = "enduser", authInfo = delegatedAuthInfo).use { fixture ->
            val response = fixture.patch(AuthorizationGrantStatus.Exhausted, onBehalfOfOrganisation = "999888771")

            response.status shouldBe HttpStatusCode.Forbidden
            val input = fixture.pdpInput()
            input.getValue("token").jsonPrimitive.content shouldBe "test-token"
            input.getValue("payload").jsonObject.getValue("OnBehalfOfOrganisationId").jsonPrimitive.content shouldBe "999888771"
            coVerify(exactly = 0) { fixture.repository.find(any()) }
            coVerify(exactly = 0) { fixture.repository.update(any(), any()) }
        }
    }

    test("PATCH unrelated person cannot revoke a grant") {
        GrantPatchFixture(
            tokenType = "enduser",
            authInfo = """{"actingId":"unrelated-person","actingType":"person","originalId":"unrelated-person"}""",
        ).use { fixture ->
            val response = fixture.patch(AuthorizationGrantStatus.Revoked)

            response.status shouldBe HttpStatusCode.Forbidden
            fixture.pdpInput().getValue("token").jsonPrimitive.content shouldBe "test-token"
            coVerify(exactly = 1) { fixture.repository.find(fixture.grant.id) }
            coVerify(exactly = 0) { fixture.repository.update(any(), any()) }
        }
    }

    test("PATCH Maskinporten cannot revoke a grant even with matching person authInfo") {
        GrantPatchFixture(
            tokenType = "maskinporten",
            authInfo = """{"actingId":"person-1","actingType":"person","originalId":"person-1"}""",
        ).use { fixture ->
            val response = fixture.patch(AuthorizationGrantStatus.Revoked)

            response.status shouldBe HttpStatusCode.Forbidden
            fixture.pdpInput().getValue("token").jsonPrimitive.content shouldBe "test-token"
            coVerify(exactly = 0) { fixture.repository.find(any()) }
            coVerify(exactly = 0) { fixture.repository.update(any(), any()) }
        }
    }
})

private class GrantPatchFixture(
    tokenType: String,
    authInfo: String,
) : AutoCloseable {
    val repository = mockk<GrantRepository>()
    val grant = AuthorizationGrant.new(
        type = AuthorizationGrantType.Consumable,
        sourceType = AuthorizationGrantSourceType.Request,
        sourceId = UUID.randomUUID(),
        principal = AuthorizationAgent("person-2", AgentType.Person),
        grantedBy = AuthorizationAgent("person-1", AgentType.Person),
        grantedTo = AuthorizationAgent("7080005053246", AgentType.OrganizationEntity),
        approvedBy = AuthorizationAgent("person-2", AgentType.Person),
        validFrom = Instant.parse("2026-10-01T08:00:00Z"),
        validTo = Instant.parse("2100-01-01T00:00:00Z"),
        scopes = emptyList(),
    )
    private val pdpRequest = CompletableFuture<String>()
    private val pdp = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    init {
        coEvery { repository.find(grant.id) } returns grant
        coEvery { repository.update(grant.id, any()) } answers { grant.copy(status = secondArg()) }
        pdp.createContext(AUTHINFO_POLICY_ROUTE) { exchange ->
            exchange.use {
                val body = exchange.requestBody.bufferedReader().use { it.readText() }
                if (exchange.requestMethod == "POST" && exchange.requestURI.path == AUTHINFO_POLICY_ROUTE) {
                    pdpRequest.complete(body)
                } else {
                    pdpRequest.completeExceptionally(AssertionError("Unexpected PDP request: ${exchange.requestURI}"))
                }
                val response = """{"result":{"tokenInfo":{"partyId":"system-1","tokenStatus":"verified","tokenType":"$tokenType"},"authInfo":$authInfo}}"""
                    .toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
        }
        pdp.start()
    }

    fun patch(status: AuthorizationGrantStatus, onBehalfOfOrganisation: String? = null): GrantPatchResponse {
        lateinit var result: GrantPatchResponse
        testApplication {
            environment {
                config = MapApplicationConfig(
                    "v1.enabled" to "true",
                    "pdp.baseUrl" to "http://127.0.0.1:${pdp.address.port}",
                )
            }
            application {
                configureRequestTracing()
                configureSerialization()
                configureErrorHandling()
                configureAuthorization()
                dependencies {
                    provide<GrantRepository> { repository }
                }
                grantsModule()
            }

            val response = client.patch("$V1_GRANTS_PATH/${grant.id}") {
                bearerAuth("test-token")
                if (onBehalfOfOrganisation != null) {
                    header(ApiHeaders.ON_BEHALF_OF_ORGANISATION, onBehalfOfOrganisation)
                }
                accept(jsonApiContentType)
                contentType(jsonApiContentType)
                setBody("""{"data":{"id":"${grant.id}","type":"ConsumableGrant","attributes":{"status":"$status"}}}""")
            }

            result = GrantPatchResponse(response.status, Json.parseToJsonElement(response.bodyAsText()).jsonObject)
        }
        return result
    }

    fun pdpInput(): JsonObject =
        Json.parseToJsonElement(pdpRequest.get(5, TimeUnit.SECONDS)).jsonObject.getValue("input").jsonObject

    override fun close() {
        pdp.stop(0)
    }
}

private data class GrantPatchResponse(val status: HttpStatusCode, val body: JsonObject) {
    fun assertGrant(id: UUID, status: AuthorizationGrantStatus) {
        val data = body.getValue("data").jsonObject
        data.getValue("id").jsonPrimitive.content shouldBe id.toString()
        data.getValue("attributes").jsonObject.getValue("status").jsonPrimitive.content shouldBe status.name
    }
}
