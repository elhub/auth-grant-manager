package no.elhub.auth.v1.api.requests.routes

import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.elhub.auth.v0.features.common.party.PartyIdentifier
import no.elhub.auth.v0.features.common.party.PartyIdentifierType
import no.elhub.auth.v0.postJson
import no.elhub.auth.v0.setupAppWith
import no.elhub.auth.v1.api.common.assertUnsupportedQueryParameters
import no.elhub.auth.v1.api.requests.response.JsonApiGetAuthorizationRequestResponse
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.requests.AuthorizationRequestSpecification
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import no.elhub.auth.v1.features.requests.CreateRequestHandler
import no.elhub.auth.v1.features.requests.creation.RedirectUriValidator
import no.elhub.auth.v1.repositories.RequestRepository
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID
import kotlin.time.Instant
import no.elhub.auth.v0.features.common.party.PartyService as AgentService

class CreateRequestRouteTest : FunSpec({
    val authorizedAgent = AuthorizationAgent("123456789", AgentType.OrganizationEntity)
    val requestedPrincipal = AuthorizationAgent("999888771", AgentType.Organization)
    val requestedOf = AuthorizationAgent("999888772", AgentType.Organization)
    val grantValidFrom = Instant.parse("2100-01-01T00:00:00Z")
    val grantValidTo = Instant.parse("2101-01-01T00:00:00Z")
    val meteringPoints = ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create("707057500000000001")))
    val requestBody = """
        {
          "data": {
            "type": "AuthorizationRequest",
            "attributes": {
              "requestType": "EnergySupplierFrameworkAgreement",
              "externalReference": "ref-1",
              "requestedGrantValidFrom": "2100-01-01T01:00:00+01:00",
              "requestedGrantValidTo": "2101-01-01T00:00:00Z"
            },
            "meta": {
              "requestedPrincipal": "999888771",
               "requestedOf": "999888772"
            }
          }
        }
    """.trimIndent()

    lateinit var handler: CreateRequestHandler
    lateinit var agentService: AgentService

    fun savedRequest() = AuthorizationRequest(
        id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
        requestType = AuthorizationRequestType.EnergySupplierFrameworkAgreement,
        status = AuthorizationRequestStatus.Pending,
        requestedScopes = emptyList(),
        externalReference = "ref-1",
        redirectUri = null,
        meta = JsonObject(emptyMap()),
        requestedGrantValidFrom = grantValidFrom,
        requestedGrantValidTo = grantValidTo,
        validFrom = Instant.parse("2026-01-01T00:00:00Z"),
        validTo = Instant.parse("2026-01-29T00:00:00Z"),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        requestedBy = authorizedAgent,
        requestedPrincipal = requestedPrincipal,
        requestedOf = requestedOf,
        approvedBy = null,
        authorizationGrants = null,
    )

    beforeAny {
        handler = mockk()
        agentService = mockk()
    }

    test("POST / rejects unsupported query parameters without calling the handler") {
        testApplication {
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
            }

            assertUnsupportedQueryParameters(client, "/", method = HttpMethod.Post)

            coVerify(exactly = 0) { agentService.resolve(any()) }
            coVerify(exactly = 0) { handler(any(), any(), any(), any()) }
        }
    }

    listOf("requestedBy", "requestedPrincipal", "requestedOf").forEach { role ->
        test("POST / returns an API-friendly error for an unsupported $role") {
            val agents = mapOf(
                "requestedBy" to authorizedAgent,
                "requestedPrincipal" to requestedPrincipal,
                "requestedOf" to requestedOf,
            ).mapValues { (agentRole, agent) -> if (agentRole == role) agent.copy(type = AgentType.Person) else agent }
            coEvery { agentService.resolve(any()) } returnsMany listOf(
                agents.getValue("requestedPrincipal").right(),
                agents.getValue("requestedOf").right(),
            )
            val repository = mockk<RequestRepository>()
            val creationHandler = CreateRequestHandler(mockk(), mockk(), mockk(), mockk<RedirectUriValidator>(relaxed = true), repository)

            testApplication {
                setupAppWith(agents.getValue("requestedBy")) {
                    route(agentService, creationHandler)
                }

                val response = client.postJson("/", requestBody)

                val body: JsonObject = response.body()
                val error = body.getValue("errors").jsonArray.single().jsonObject
                if (role == "requestedBy") {
                    response.status shouldBe HttpStatusCode.Forbidden
                    error.getValue("status").jsonPrimitive.content shouldBe "403"
                    error.getValue("title").jsonPrimitive.content shouldBe "Caller not allowed"
                    error.getValue("detail").jsonPrimitive.content shouldBe
                        "Only authenticated market entities can create requests of type 'EnergySupplierFrameworkAgreement'."
                    error["source"] shouldBe null
                } else {
                    response.status shouldBe HttpStatusCode.UnprocessableEntity
                    error.getValue("status").jsonPrimitive.content shouldBe "422"
                    error.getValue("title").jsonPrimitive.content shouldBe
                        if (role == "requestedPrincipal") "Invalid requestedPrincipal" else "Invalid requestedOf"
                    error.getValue("detail").jsonPrimitive.content shouldBe
                        "data.meta.$role must identify an organization for request type 'EnergySupplierFrameworkAgreement'."
                    error["source"] shouldBe null
                }
                coVerify(exactly = 0) { repository.insert(any()) }
            }
        }
    }

    test("POST / creates a framework agreement request and returns requested grant validity") {
        coEvery { handler(any(), any(), any(), any()) } returns savedRequest()
        coEvery { agentService.resolve(any()) } returnsMany listOf(requestedPrincipal.right(), requestedOf.right())

        testApplication {
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
            }

            val response = client.postJson("/", requestBody)

            response.status shouldBe HttpStatusCode.Created
            val body: JsonApiGetAuthorizationRequestResponse = response.body()
            body.data.type shouldBe "AuthorizationRequest"
            body.data.attributes.requestType shouldBe AuthorizationRequestType.EnergySupplierFrameworkAgreement
            body.data.attributes.requestedGrantValidFrom shouldBe grantValidFrom
            body.data.attributes.requestedGrantValidTo shouldBe grantValidTo
            body.data.relationships.requestedBy.data.id shouldBe authorizedAgent.id
            coVerify(exactly = 1) {
                agentService.resolve(PartyIdentifier(PartyIdentifierType.OrganizationNumber, "999888771"))
            }
            coVerify(exactly = 1) {
                agentService.resolve(PartyIdentifier(PartyIdentifierType.OrganizationNumber, "999888772"))
            }
            coVerify(exactly = 1) {
                handler(
                    specification = AuthorizationRequestSpecification(
                        externalReference = "ref-1",
                        redirectUri = null,
                        details = AuthorizationRequestTypeDetails.EnergySupplierFrameworkAgreement(
                            requestedGrantValidFrom = grantValidFrom,
                            requestedGrantValidTo = grantValidTo,
                        ),
                    ),
                    requestedBy = authorizedAgent,
                    requestedPrincipal = requestedPrincipal,
                    requestedOf = requestedOf,
                )
            }
        }
    }

    fun attributesWithScope(requestType: String, allowedChanges: String = "") = """
        "attributes": {
          "requestType": "$requestType",
          "externalReference": "ref-1",
          "requestedScopes": [{
            "resourceType": "MeteringPointContract",
            "appliesTo": [{ "attribute": "meteringPoint.id", "value": ["707057500000000001"] }]$allowedChanges
          }]
        },
    """.trimIndent()

    fun scopedRequestBody(requestType: String, allowedChanges: String = "") =
        requestBody.replace(
            Regex("\"attributes\": \\{.*?\\},", RegexOption.DOT_MATCHES_ALL),
            attributesWithScope(requestType, allowedChanges),
        )

    test("POST / creates a change of energy supplier request") {
        val saved = savedRequest().copy(
            requestType = AuthorizationRequestType.ChangeOfEnergySupplierForOrganization,
            requestedGrantValidFrom = null,
            requestedGrantValidTo = null,
        )
        coEvery { handler(any(), any(), any(), any()) } returns saved
        coEvery { agentService.resolve(any()) } returnsMany listOf(requestedPrincipal.right(), requestedOf.right())

        testApplication {
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
            }

            val response = client.postJson("/", scopedRequestBody("ChangeOfEnergySupplierForOrganization"))

            response.status shouldBe HttpStatusCode.Created
            val body: JsonApiGetAuthorizationRequestResponse = response.body()
            body.data.attributes.requestType shouldBe AuthorizationRequestType.ChangeOfEnergySupplierForOrganization
            body.data.attributes.requestedGrantValidFrom shouldBe null
            body.data.attributes.requestedGrantValidTo shouldBe null
            coVerify(exactly = 1) {
                handler(
                    specification = AuthorizationRequestSpecification(
                        externalReference = "ref-1",
                        redirectUri = null,
                        details = AuthorizationRequestTypeDetails.ChangeOfEnergySupplierForOrganization(meteringPoints),
                    ),
                    requestedBy = authorizedAgent,
                    requestedPrincipal = requestedPrincipal,
                    requestedOf = requestedOf,
                )
            }
        }
    }

    test("POST / includes the request's authorization text reference") {
        coEvery { handler(any(), any(), any(), any()) } returns savedRequest().copy(textReference = "v2")
        coEvery { agentService.resolve(any()) } returnsMany listOf(requestedPrincipal.right(), requestedOf.right())

        testApplication {
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
            }

            val response = client.postJson("/", scopedRequestBody("ChangeOfEnergySupplierForOrganization"))

            response.status shouldBe HttpStatusCode.Created
            response.bodyAsText() shouldContain "\"textReference\":\"v2\""
        }
    }

    test("POST / creates a move-in and change of energy supplier request") {
        val saved = savedRequest().copy(
            requestType = AuthorizationRequestType.MoveInAndChangeOfEnergySupplierForOrganization,
            requestedGrantValidFrom = null,
            requestedGrantValidTo = null,
        )
        coEvery { handler(any(), any(), any(), any()) } returns saved
        coEvery { agentService.resolve(any()) } returnsMany listOf(requestedPrincipal.right(), requestedOf.right())

        testApplication {
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
            }

            val response = client.postJson(
                "/",
                scopedRequestBody(
                    "MoveInAndChangeOfEnergySupplierForOrganization",
                    """,
                    "allowedChanges": [{ "attribute": "validFrom", "value": ["2026-10-01"] }]""",
                ),
            )

            response.status shouldBe HttpStatusCode.Created
            val body: JsonApiGetAuthorizationRequestResponse = response.body()
            body.data.attributes.requestType shouldBe AuthorizationRequestType.MoveInAndChangeOfEnergySupplierForOrganization
            body.data.attributes.requestedGrantValidFrom shouldBe null
            body.data.attributes.requestedGrantValidTo shouldBe null
            coVerify(exactly = 1) {
                handler(
                    specification = AuthorizationRequestSpecification(
                        externalReference = "ref-1",
                        redirectUri = null,
                        details = AuthorizationRequestTypeDetails.MoveInAndChangeOfEnergySupplierForOrganization(
                            meteringPointIds = meteringPoints,
                            moveInDate = LocalDate(2026, 10, 1),
                        ),
                    ),
                    requestedBy = authorizedAgent,
                    requestedPrincipal = requestedPrincipal,
                    requestedOf = requestedOf,
                )
            }
        }
    }

    test("POST / returns 409 when the resource type is wrong") {
        testApplication {
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
            }

            val response = client.postJson("/", requestBody.replace("\"AuthorizationRequest\"", "\"OtherResource\""))

            response.status shouldBe HttpStatusCode.Conflict
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().detail shouldBe
                "Expected 'data.type' to be 'AuthorizationRequest', but received 'OtherResource'"
            coVerify(exactly = 0) { handler(any(), any(), any(), any()) }
        }
    }

    listOf(
        "ChangeOfEnergySupplierForOrganization",
        "MoveInAndChangeOfEnergySupplierForOrganization",
    ).forEach { type ->
        test("POST / returns 422 for duplicate metering-point IDs in $type") {
            testApplication {
                setupAppWith(authorizedAgent) {
                    route(agentService, handler)
                }

                val body = scopedRequestBody(type).replace(
                    "\"value\": [\"707057500000000001\"]",
                    "\"value\": [\"707057500000000001\", \"707057500000000001\"]",
                )
                val response = client.postJson("/", body)

                response.status shouldBe HttpStatusCode.UnprocessableEntity
                val error: JsonApiErrorCollection = response.body()
                error.errors.single().detail shouldBe
                    "requestedScopes[0].appliesTo meteringPoint.id must not contain duplicate metering points"
                coVerify(exactly = 0) { agentService.resolve(any()) }
                coVerify(exactly = 0) { handler(any(), any(), any(), any()) }
            }
        }
    }

    test("POST / returns 422 when requested grant validity is not in order") {
        testApplication {
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
            }

            val response = client.postJson("/", requestBody.replace("2101-01-01T00:00:00Z", "2099-01-01T00:00:00Z"))

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().detail shouldBe "requestedGrantValidFrom must be before requestedGrantValidTo"
            coVerify(exactly = 0) { agentService.resolve(any()) }
            coVerify(exactly = 0) { handler(any(), any(), any(), any()) }
        }
    }

    test("POST / returns 422 when requested grant validity is sent for change of supplier") {
        testApplication {
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
            }

            val response = client.postJson(
                "/",
                requestBody.replace("EnergySupplierFrameworkAgreement", "ChangeOfEnergySupplierForOrganization"),
            )

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().detail shouldBe
                "requestedGrantValidFrom and requestedGrantValidTo are not supported for ChangeOfEnergySupplierForOrganization"
            coVerify(exactly = 0) { handler(any(), any(), any(), any()) }
        }
    }
})
