package no.elhub.auth.v1.api.requests.routes

import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyIdentifier
import no.elhub.auth.v0.features.common.party.PartyIdentifierType
import no.elhub.auth.v0.features.common.party.PartyService
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v0.postJson
import no.elhub.auth.v0.setupAppWith
import no.elhub.auth.v1.api.requests.response.JsonApiGetAuthorizationRequestResponse
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.AuthorizationRequestStatus
import no.elhub.auth.v1.domain.AuthorizationRequestType
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.requests.AuthorizationRequestSpecification
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import no.elhub.auth.v1.features.requests.CreateRequestHandler
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import java.util.UUID
import kotlin.time.Instant

class CreateRequestRouteTest : FunSpec({
    val authorizedParty = AuthorizationParty("123456789", PartyType.OrganizationEntity)
    val requestedPrincipal = AuthorizationParty("999888777", PartyType.OrganizationEntity)
    val requestedOf = AuthorizationParty("person-1", PartyType.Person)
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
              "requestedPrincipal": "999888777",
              "requestedOf": "01019012345"
            }
          }
        }
    """.trimIndent()

    lateinit var handler: CreateRequestHandler
    lateinit var partyService: PartyService

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
        requestedBy = authorizedParty,
        requestedPrincipal = requestedPrincipal,
        requestedOf = requestedOf,
        approvedBy = null,
        authorizationGrants = null,
    )

    beforeAny {
        handler = mockk()
        partyService = mockk()
    }

    test("POST / creates a framework agreement request and returns requested grant validity") {
        coEvery { handler(any(), any(), any(), any()) } returns savedRequest()
        coEvery { partyService.resolve(any()) } returnsMany listOf(requestedPrincipal.right(), requestedOf.right())

        testApplication {
            setupAppWith(authorizedParty) {
                route(partyService, handler)
            }

            val response = client.postJson("/", requestBody)

            response.status shouldBe HttpStatusCode.Created
            val body: JsonApiGetAuthorizationRequestResponse = response.body()
            body.data.type shouldBe "AuthorizationRequest"
            body.data.attributes.requestType shouldBe AuthorizationRequestType.EnergySupplierFrameworkAgreement
            body.data.attributes.requestedGrantValidFrom shouldBe grantValidFrom
            body.data.attributes.requestedGrantValidTo shouldBe grantValidTo
            body.data.relationships.requestedBy.data.id shouldBe authorizedParty.id
            coVerify(exactly = 1) {
                partyService.resolve(PartyIdentifier(PartyIdentifierType.OrganizationNumber, "999888777"))
            }
            coVerify(exactly = 1) {
                partyService.resolve(PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "01019012345"))
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
                    requestedBy = authorizedParty,
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
        coEvery { partyService.resolve(any()) } returnsMany listOf(requestedPrincipal.right(), requestedOf.right())

        testApplication {
            setupAppWith(authorizedParty) {
                route(partyService, handler)
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
                    requestedBy = authorizedParty,
                    requestedPrincipal = requestedPrincipal,
                    requestedOf = requestedOf,
                )
            }
        }
    }

    test("POST / creates a move-in and change of energy supplier request") {
        val saved = savedRequest().copy(
            requestType = AuthorizationRequestType.MoveInAndChangeOfEnergySupplierForOrganization,
            requestedGrantValidFrom = null,
            requestedGrantValidTo = null,
        )
        coEvery { handler(any(), any(), any(), any()) } returns saved
        coEvery { partyService.resolve(any()) } returnsMany listOf(requestedPrincipal.right(), requestedOf.right())

        testApplication {
            setupAppWith(authorizedParty) {
                route(partyService, handler)
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
                    requestedBy = authorizedParty,
                    requestedPrincipal = requestedPrincipal,
                    requestedOf = requestedOf,
                )
            }
        }
    }

    test("POST / returns 409 when the resource type is wrong") {
        testApplication {
            setupAppWith(authorizedParty) {
                route(partyService, handler)
            }

            val response = client.postJson("/", requestBody.replace("\"AuthorizationRequest\"", "\"OtherResource\""))

            response.status shouldBe HttpStatusCode.Conflict
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().detail shouldBe
                "Expected 'data.type' to be 'AuthorizationRequest', but received 'OtherResource'"
            coVerify(exactly = 0) { handler(any(), any(), any(), any()) }
        }
    }

    test("POST / returns 422 when requested grant validity is not in order") {
        testApplication {
            setupAppWith(authorizedParty) {
                route(partyService, handler)
            }

            val response = client.postJson("/", requestBody.replace("2101-01-01T00:00:00Z", "2099-01-01T00:00:00Z"))

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().detail shouldBe "requestedGrantValidFrom must be before requestedGrantValidTo"
            coVerify(exactly = 0) { partyService.resolve(any()) }
            coVerify(exactly = 0) { handler(any(), any(), any(), any()) }
        }
    }

    test("POST / returns 422 when requested grant validity is sent for change of supplier") {
        testApplication {
            setupAppWith(authorizedParty) {
                route(partyService, handler)
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
