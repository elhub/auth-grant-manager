package no.elhub.auth.v1.features.documents.create

import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.common.documents.pdf.PdfGenerator
import no.elhub.auth.common.documents.pdf.PdfSigner
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyIdentifier
import no.elhub.auth.v0.features.common.party.PartyIdentifierType
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
import no.elhub.auth.v1.domain.MeteringPointContractType
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import no.elhub.auth.v1.domain.validMeteringPointId
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
              "requestedFrom": "999888777",
              "requestedTo": "01019012345",
              "language": "nb"
            }
          }
        }
    """.trimIndent()

    lateinit var handler: DocumentService
    lateinit var pdfGenerator: PdfGenerator
    lateinit var pdfSigner: PdfSigner
    lateinit var partyService: PartyService
    lateinit var changeOfEnergySupplierActionValidator: ChangeOfEnergySupplierDocumentActionValidator
    lateinit var moveInAndChangeOfEnergySupplierActionValidator: MoveInAndChangeOfEnergySupplierDocumentActionValidator

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
                        value = ResourceConstraint.MeteringPoints(setOf(validMeteringPointId("707057500000000001"))),
                    ),
                    AuthorizationScopeConstraint(
                        constraintKind = AuthorizationScopeConstraintKind.AllowedChanges,
                        attribute = AuthorizationScopeConstraintAttribute.ContractType,
                        value = ResourceConstraint.ContractTypes(setOf(MeteringPointContractType.EnergySupplier)),
                    ),
                ),
            ),
        ),
        externalReference = "contract-123",
        meta = JsonObject(emptyMap()),
        validTo = Clock.System.now(),
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
        requestedBy = authorizedParty,
        requestedFrom = AuthorizationParty("999888777", PartyType.OrganizationEntity),
        requestedTo = AuthorizationParty("person-1", PartyType.Person),
        signedBy = null,
        authorizationGrant = null,
        pdfBytes = ByteArray(0),
    )

    beforeAny {
        handler = mockk()
        pdfGenerator = mockk()
        pdfSigner = mockk()
        partyService = mockk()
        changeOfEnergySupplierActionValidator = ChangeOfEnergySupplierDocumentActionValidator()
        moveInAndChangeOfEnergySupplierActionValidator = MoveInAndChangeOfEnergySupplierDocumentActionValidator()
    }

    test("POST / returns 201 and passes the authorized party to the handler") {
        coEvery {
            handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any())
        } returns responseDocument()
        coEvery { partyService.resolve(any()) } returnsMany listOf(
            AuthorizationParty("999888777", PartyType.OrganizationEntity).right(),
            AuthorizationParty("person-1", PartyType.Person).right(),
        )

        testApplication {
            setupAppWith(authorizedParty) {
                route(partyService, handler, changeOfEnergySupplierActionValidator, moveInAndChangeOfEnergySupplierActionValidator)
            }

            val response = client.postJson("/", requestBody)

            response.status shouldBe HttpStatusCode.Created
            val body: JsonApiCreateAuthorizationDocumentResponse = response.body()
            body.data.type shouldBe "AuthorizationDocument"
            body.data.id shouldBe body.data.links.self.substringAfterLast('/')
            body.data.attributes.documentType shouldBe AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization
            body.data.attributes.status.name shouldBe "Pending"
            val returnedScope = body.data.attributes.requestedScopes.single()
            returnedScope.resourceType shouldBe ResourceType.MeteringPointContract
            returnedScope.appliesTo.single().attribute shouldBe "meteringPoint.id"
            returnedScope.appliesTo.single().value shouldBe listOf("707057500000000001")
            returnedScope.allowedChanges.single().attribute shouldBe "type"
            returnedScope.allowedChanges.single().value shouldBe listOf("EnergySupplier")
            coVerify(exactly = 1) {
                partyService.resolve(PartyIdentifier(PartyIdentifierType.OrganizationNumber, "999888777"))
            }
            coVerify(exactly = 1) {
                partyService.resolve(PartyIdentifier(PartyIdentifierType.NationalIdentityNumber, "01019012345"))
            }
            coVerify(exactly = 1) {
                handler.createAuthorizationDocument(
                    action = match {
                        it.documentType == AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization
                    },
                    externalReference = "contract-123",
                    requestedBy = authorizedParty,
                    requestedFrom = AuthorizationParty("999888777", PartyType.OrganizationEntity),
                    requestedTo = AuthorizationParty("person-1", PartyType.Person),
                    language = DocumentLanguage.NB,
                )
            }
        }
    }

    test("POST / defaults the document language to Norwegian Bokmål") {
        coEvery {
            handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any())
        } returns responseDocument()
        coEvery { partyService.resolve(any()) } returnsMany listOf(
            AuthorizationParty("999888777", PartyType.OrganizationEntity).right(),
            AuthorizationParty("person-1", PartyType.Person).right(),
        )

        testApplication {
            setupAppWith(authorizedParty) {
                route(partyService, handler, changeOfEnergySupplierActionValidator, moveInAndChangeOfEnergySupplierActionValidator)
            }

            val response = client.postJson("/", requestBody.replace(Regex(",\\s*\"language\": \"nb\""), ""))

            response.status shouldBe HttpStatusCode.Created
            coVerify(exactly = 1) {
                handler.createAuthorizationDocument(
                    action = any(),
                    externalReference = "contract-123",
                    requestedBy = authorizedParty,
                    requestedFrom = AuthorizationParty("999888777", PartyType.OrganizationEntity),
                    requestedTo = AuthorizationParty("person-1", PartyType.Person),
                    language = DocumentLanguage.NB,
                )
            }
        }
    }

    test("document service uses action details to generate and sign the document") {
        val changeSupplierHandler: ChangeOfEnergySupplierDocumentActionHandler = mockk()
        val moveInHandler: MoveInAndChangeOfEnergySupplierDocumentActionHandler = mockk()
        val action = DocumentAction.ChangeOfEnergySupplierForOrganization(
            ResourceConstraint.MeteringPoints(setOf(validMeteringPointId("707057500000000001"))),
        )
        val pdfContent = mockk<no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent>()
        val unsignedPdf = byteArrayOf(1, 2)
        val signedPdf = byteArrayOf(3, 4)
        val validTo = Clock.System.now()
        val meta = JsonObject(mapOf("source" to kotlinx.serialization.json.JsonPrimitive("placeholder")))
        coEvery { changeSupplierHandler.validateAndGetDetails(action, any()) } returns
            DocumentActionDetails(pdfContent, validTo, meta)
        every { pdfGenerator.generate(pdfContent) } returns unsignedPdf
        coEvery { pdfSigner.sign(unsignedPdf) } returns signedPdf

        val service = DocumentService(changeSupplierHandler, moveInHandler, pdfGenerator, pdfSigner)
        val document = service.createAuthorizationDocument(
            action = action,
            externalReference = "ref",
            requestedBy = authorizedParty,
            requestedFrom = AuthorizationParty("org-1", PartyType.Organization),
            requestedTo = AuthorizationParty("person-1", PartyType.Person),
            language = DocumentLanguage.NB,
        )

        document.validTo shouldBe validTo
        document.meta shouldBe meta
        document.pdfBytes.toList() shouldBe signedPdf.toList()
        coVerify(exactly = 0) { moveInHandler.validateAndGetDetails(any(), any()) }
    }

    test("POST / returns 409 when the resource type is wrong") {
        testApplication {
            setupAppWith(authorizedParty) {
                route(partyService, handler, changeOfEnergySupplierActionValidator, moveInAndChangeOfEnergySupplierActionValidator)
            }

            val response = client.postJson("/", requestBody.replace("AuthorizationDocument", "OtherResource"))

            response.status shouldBe HttpStatusCode.Conflict
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().detail shouldBe "Expected 'data.type' to be 'AuthorizationDocument', but received 'OtherResource'"
            coVerify(exactly = 0) {
                handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any())
            }
        }
    }

    test("POST / returns a field-specific 400 when the request cannot be deserialized") {
        testApplication {
            setupAppWith(authorizedParty) {
                route(partyService, handler, changeOfEnergySupplierActionValidator, moveInAndChangeOfEnergySupplierActionValidator)
            }

            val response = client.postJson("/", "{ \"data\": { \"type\": \"AuthorizationDocument\" } }")

            response.status shouldBe HttpStatusCode.BadRequest
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().title shouldBe "Missing required field in request body"
            body.errors.single().detail shouldBe "Field '[attributes, meta]' is missing or invalid"
            coVerify(exactly = 0) {
                handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any())
            }
        }
    }

    test("POST / rejects validFrom changes that are unsupported for change of supplier") {
        testApplication {
            setupAppWith(authorizedParty) {
                route(partyService, handler, changeOfEnergySupplierActionValidator, moveInAndChangeOfEnergySupplierActionValidator)
            }

            val requestedScope = """
                "requestedScopes": [{
                  "resourceType": "MeteringPointContract",
                  "appliesTo": [{
                    "attribute": "meteringPoint.id",
                    "value": ["707057500000000001"]
                  }],
                  "allowedChanges": [{ "attribute": "validFrom", "value": ["2026-10-01"] }]
                }]
            """.trimIndent()
            val response = client.postJson(
                "/",
                requestBody.replace(
                    "\"externalReference\": \"contract-123\"",
                    "\"externalReference\": \"contract-123\",$requestedScope",
                ),
            )

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().detail shouldBe
                "requestedScopes[0].allowedChanges is not supported for ChangeOfEnergySupplierForOrganization"
            coVerify(exactly = 0) { partyService.resolve(any()) }
            coVerify(exactly = 0) { handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any()) }
        }
    }

    test("POST / returns 422 when move-in validFrom contains multiple dates") {
        testApplication {
            setupAppWith(authorizedParty) {
                route(partyService, handler, changeOfEnergySupplierActionValidator, moveInAndChangeOfEnergySupplierActionValidator)
            }

            val requestedScope = """
                "requestedScopes": [{
                  "resourceType": "MeteringPointContract",
                  "appliesTo": [{
                    "attribute": "meteringPoint.id",
                    "value": ["707057500000000001"]
                  }],
                  "allowedChanges": [{
                    "attribute": "type",
                    "value": ["EnergySupplier", "EndUser"]
                  }, {
                    "attribute": "validFrom",
                    "value": ["2026-10-01", "2026-11-01"]
                  }]
                }]
            """.trimIndent()
            val response = client.postJson(
                "/",
                requestBody
                    .replace("ChangeOfEnergySupplierForOrganization", "MoveInAndChangeOfEnergySupplierForOrganization")
                    .replace("\"externalReference\": \"contract-123\"", "\"externalReference\": \"contract-123\",$requestedScope")
            )

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().detail shouldBe
                "requestedScopes[0].allowedChanges validFrom must contain exactly one date"
            coVerify(exactly = 0) { partyService.resolve(any()) }
            coVerify(exactly = 0) {
                handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any())
            }
        }
    }

    test("POST / returns 422 when move-in does not contain the exact allowed contract types") {
        testApplication {
            setupAppWith(authorizedParty) {
                route(partyService, handler, changeOfEnergySupplierActionValidator, moveInAndChangeOfEnergySupplierActionValidator)
            }

            val requestedScope = """
                "requestedScopes": [{
                  "resourceType": "MeteringPointContract",
                  "appliesTo": [{
                    "attribute": "meteringPoint.id",
                    "value": ["707057500000000001"]
                  }],
                  "allowedChanges": [
                    { "attribute": "type", "value": ["EnergySupplier"] },
                    { "attribute": "validFrom", "value": ["2026-10-01"] }
                  ]
                }]
            """.trimIndent()
            val response = client.postJson(
                "/",
                requestBody
                    .replace("ChangeOfEnergySupplierForOrganization", "MoveInAndChangeOfEnergySupplierForOrganization")
                    .replace("\"externalReference\": \"contract-123\"", "\"externalReference\": \"contract-123\",$requestedScope"),
            )

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().detail shouldBe
                "requestedScopes[0].allowedChanges type must contain exactly [EndUser, EnergySupplier]"
        }
    }

    test("POST / returns 422 when a metering-point ID has an invalid format") {
        testApplication {
            setupAppWith(authorizedParty) {
                route(partyService, handler, changeOfEnergySupplierActionValidator, moveInAndChangeOfEnergySupplierActionValidator)
            }

            val response = client.postJson(
                "/",
                requestBody.replace("707057500000000001", "invalid"),
            )

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().detail shouldBe
                "requestedScopes[0].appliesTo meteringPoint.id values must be valid 18-digit metering-point IDs"
            coVerify(exactly = 0) { partyService.resolve(any()) }
            coVerify(exactly = 0) {
                handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any())
            }
        }
    }

    test("POST / returns 422 when requested scope has an unsupported resource type") {
        testApplication {
            setupAppWith(authorizedParty) {
                route(partyService, handler, changeOfEnergySupplierActionValidator, moveInAndChangeOfEnergySupplierActionValidator)
            }

            val response = client.postJson(
                "/",
                requestBody.replace("MeteringPointContract", "OtherResource"),
            )

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().detail shouldBe
                "requestedScopes[0].resourceType must be 'MeteringPointContract'"
            coVerify(exactly = 0) { partyService.resolve(any()) }
            coVerify(exactly = 0) { handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any()) }
        }
    }
})
