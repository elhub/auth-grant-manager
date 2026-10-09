package no.elhub.auth.v1.features.documents.create

import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyService
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v0.postJson
import no.elhub.auth.v0.setupAppWith
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import no.elhub.auth.v1.features.documents.create.dto.JsonApiCreateAuthorizationDocumentResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import kotlin.time.Clock

class RouteTest : FunSpec({
    val authorizedParty = AuthorizationParty("123456789", PartyType.OrganizationEntity)
    val requestBody = """
        {
          "data": {
            "type": "AuthorizationDocument",
            "attributes": {
              "documentType": "ChangeOfEnergySupplierForOrganization",
              "requestedScopes": [{
                "resourceType": "MeteringPointContract",
                "appliesTo": [{
                  "attribute": "meteringPoint.id",
                  "value": ["707057500000000001"]
                }]
              }],
              "externalReference": "contract-123"
            },
            "meta": {
              "requestedFrom": {
                "idType": "GlobalLocationNumber",
                "idValue": "999888777"
              },
              "requestedTo": {
                "idType": "NationalIdentityNumber",
                "idValue": "01019012345"
              },
              "language": "Nb"
            }
          }
        }
    """.trimIndent()

    lateinit var handler: CreateDocumentBusinessHandler
    lateinit var partyService: PartyService
    lateinit var payloadValidator: CreateAuthorizationDocumentPayloadValidator

    fun responseDocument() = AuthorizationDocument(
        id = "123e4567-e89b-12d3-a456-426614174000",
        documentType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization,
        status = AuthorizationDocumentStatus.Pending,
        requestedScopes = listOf(
            RequestedAuthorizationScope(
                resourceType = ResourceType.MeteringPointContract,
                constraints = listOf(
                    AuthorizationScopeConstraint(
                        constraintKind = AuthorizationScopeConstraintKind.AppliesTo,
                        attribute = AuthorizationScopeConstraintAttribute.MeteringPointId,
                        value = ResourceConstraint.MeteringPoints(
                            setOf(MeteringPointId.create("707057500000000001")),
                        ),
                    ),
                ),
            ),
        ),
        externalReference = "contract-123",
        validTo = null,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
        requestedBy = authorizedParty,
        requestedFrom = AuthorizationParty("999888777", PartyType.OrganizationEntity),
        requestedTo = AuthorizationParty("person-1", PartyType.Person),
        signedBy = null,
        authorizationGrants = emptyList(),
        pdfBytes = ByteArray(0),
    )

    beforeAny {
        handler = mockk()
        partyService = mockk()
        payloadValidator = CreateAuthorizationDocumentPayloadValidator()
    }

    test("POST / returns 201 and passes the authorized party to the handler") {
        coEvery {
            handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any(), any())
        } returns responseDocument()
        coEvery { partyService.resolve(any()) } returnsMany listOf(
            AuthorizationParty("999888777", PartyType.OrganizationEntity).right(),
            AuthorizationParty("person-1", PartyType.Person).right(),
        )

        testApplication {
            setupAppWith(authorizedParty) { route(partyService, handler, payloadValidator) }

            val response = client.postJson("/", requestBody)

            response.status shouldBe HttpStatusCode.Created
            val body: JsonApiCreateAuthorizationDocumentResponse = response.body()
            body.data.type shouldBe "AuthorizationDocument"
            body.data.id shouldBe body.data.links.self.substringAfterLast('/')
            body.data.attributes.documentType shouldBe AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization
            body.data.attributes.status.name shouldBe "Pending"
            body.data.meta.language shouldBe DocumentLanguage.Nb
            coVerify(exactly = 1) {
                handler.createAuthorizationDocument(
                    documentType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization,
                    requestedScope = match {
                        it.resourceType == ResourceType.MeteringPointContract
                    },
                    externalReference = "contract-123",
                    requestedBy = authorizedParty,
                    requestedFrom = AuthorizationParty("999888777", PartyType.OrganizationEntity),
                    requestedTo = AuthorizationParty("person-1", PartyType.Person),
                    language = DocumentLanguage.Nb,
                )
            }
        }
    }

    test("POST / returns 409 when the resource type is wrong") {
        testApplication {
            setupAppWith(authorizedParty) { route(partyService, handler, payloadValidator) }

            val response = client.postJson("/", requestBody.replace("AuthorizationDocument", "OtherResource"))

            response.status shouldBe HttpStatusCode.Conflict
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().detail shouldBe "Expected 'data.type' to be 'AuthorizationDocument', but received 'OtherResource'"
            coVerify(exactly = 0) {
                handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any(), any())
            }
        }
    }

    test("POST / returns 400 when the request cannot be deserialized") {
        testApplication {
            setupAppWith(authorizedParty) { route(partyService, handler, payloadValidator) }

            val response = client.postJson("/", "{ \"data\": { \"type\": \"AuthorizationDocument\" } }")

            response.status shouldBe HttpStatusCode.BadRequest
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().title shouldBe "Invalid request body"
            coVerify(exactly = 0) {
                handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any(), any())
            }
        }
    }

    test("POST / ignores allowed changes that are not used by change of supplier") {
        coEvery {
            handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any(), any())
        } returns responseDocument()
        coEvery { partyService.resolve(any()) } returnsMany listOf(
            AuthorizationParty("999888777", PartyType.OrganizationEntity).right(),
            AuthorizationParty("person-1", PartyType.Person).right(),
        )

        testApplication {
            setupAppWith(authorizedParty) { route(partyService, handler, payloadValidator) }

            val requestedScope = """
                "requestedScopes": [{
                  "resourceType": "MeteringPointContract",
                  "appliesTo": [{"attribute": "meteringPoint.id", "value": ["707057500000000001"]}],
                  "allowedChanges": [{"attribute": "ignored", "value": ["value"]}]
                }]
            """.trimIndent()
            val response = client.postJson(
                "/",
                requestBody.replace(
                    "\"externalReference\": \"contract-123\"",
                    "\"externalReference\": \"contract-123\",$requestedScope",
                ),
            )

            response.status shouldBe HttpStatusCode.Created
            coVerify(exactly = 1) {
                handler.createAuthorizationDocument(
                    documentType = AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization,
                    requestedScope = match { it.resourceType == ResourceType.MeteringPointContract },
                    externalReference = any(),
                    requestedBy = any(),
                    requestedFrom = any(),
                    requestedTo = any(),
                    language = any(),
                )
            }
        }
    }

    test("POST / returns 422 when move in does not contain exactly one valid-from date") {
        testApplication {
            setupAppWith(authorizedParty) { route(partyService, handler, payloadValidator) }

            val requestedScope = """
                "requestedScopes": [{
                  "resourceType": "MeteringPointContract",
                  "appliesTo": [{"attribute": "meteringPoint.id", "value": ["707057500000000001"]}],
                  "allowedChanges": [{"attribute": "validFrom", "value": ["2026-10-01T00:00:00Z", "2026-11-01T00:00:00Z"]}]
                }]
            """.trimIndent()
            val response = client.postJson(
                "/",
                requestBody
                    .replace("ChangeOfEnergySupplierForOrganization", "MoveInAndChangeOfEnergySupplierForOrganization")
                    .replace("\"externalReference\": \"contract-123\"", "\"externalReference\": \"contract-123\",$requestedScope")
            )

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            coVerify(exactly = 0) { partyService.resolve(any()) }
            coVerify(exactly = 0) {
                handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any(), any())
            }
        }
    }

    test("POST / returns 422 when a metering-point ID has an invalid format") {
        testApplication {
            setupAppWith(authorizedParty) { route(partyService, handler, payloadValidator) }

            val response = client.postJson(
                "/",
                requestBody.replace("707057500000000001", "invalid"),
            )

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().detail shouldBe
                "requestedScopes.appliesTo.meteringPoint.id must contain only valid 18-digit metering-point IDs"
            coVerify(exactly = 0) { partyService.resolve(any()) }
            coVerify(exactly = 0) {
                handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any(), any())
            }
        }
    }
})
