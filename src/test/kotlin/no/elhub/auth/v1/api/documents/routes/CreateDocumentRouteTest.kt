package no.elhub.auth.v1.api.documents.routes

import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.elhub.auth.common.documents.pdf.PdfGenerator
import no.elhub.auth.common.documents.pdf.PdfSigner
import no.elhub.auth.v0.postJson
import no.elhub.auth.v0.setupAppWith
import no.elhub.auth.v1.api.common.assertUnsupportedQueryParameters
import no.elhub.auth.v1.api.common.toPartyIdentifier
import no.elhub.auth.v1.api.documents.response.JsonApiGetAuthorizationDocumentResponse
import no.elhub.auth.v1.common.AuthorizationRulesService
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AgentExternalIdentifier
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationScopeConstraint
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintAttribute
import no.elhub.auth.v1.domain.AuthorizationScopeConstraintKind
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.auth.v1.domain.MeteringPointContractType
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.RequestedAuthorizationScope
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.ResourceType
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.CreateDocumentHandler
import no.elhub.auth.v1.features.documents.creation.ChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.features.documents.creation.DocumentCreationDetails
import no.elhub.auth.v1.features.documents.creation.DocumentPreparationError
import no.elhub.auth.v1.features.documents.creation.MoveInAndChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.repositories.DocumentRepository
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection
import kotlin.time.Clock
import kotlin.time.Instant
import no.elhub.auth.v0.features.common.party.PartyService as AgentService

class CreateDocumentRouteTest : FunSpec({
    val authorizedAgent = AuthorizationAgent("123456789", AgentType.OrganizationEntity)
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
              "requestedPrincipal": "999888771",
               "requestedOf": "999888772",
              "language": "nb"
            }
          }
        }
    """.trimIndent()

    lateinit var handler: CreateDocumentHandler
    lateinit var pdfGenerator: PdfGenerator
    lateinit var pdfSigner: PdfSigner
    lateinit var agentService: AgentService

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
                        value = ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create("707057500000000001"))),
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
        validFrom = Instant.parse("2026-10-01T08:00:00Z"),
        validTo = Clock.System.now(),
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
        requestedBy = authorizedAgent,
        requestedPrincipal = AuthorizationAgent("999888771", AgentType.Organization),
        requestedOf = AuthorizationAgent("999888772", AgentType.Organization),
        approvedBy = null,
        authorizationGrants = null,
        pdfBytes = ByteArray(0),
    )

    beforeAny {
        handler = mockk()
        pdfGenerator = mockk()
        pdfSigner = mockk()
        agentService = mockk()
    }

    test("POST / rejects unsupported query parameters without calling the handler") {
        testApplication {
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
            }

            assertUnsupportedQueryParameters(client, "/", method = HttpMethod.Post)

            coVerify(exactly = 0) { agentService.resolve(any()) }
            coVerify(exactly = 0) { handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any()) }
        }
    }

    listOf("requestedBy", "requestedPrincipal", "requestedOf").forEach { role ->
        test("POST / returns an API-friendly error for an unsupported $role") {
            val requestedBy = if (role == "requestedBy") authorizedAgent.copy(type = AgentType.Person) else authorizedAgent
            val requestedPrincipal = AuthorizationAgent(
                "999888771",
                if (role == "requestedPrincipal") AgentType.Person else AgentType.Organization,
            )
            val requestedOf = AuthorizationAgent(
                "999888772",
                if (role == "requestedOf") AgentType.Person else AgentType.Organization,
            )
            coEvery { agentService.resolve(any()) } returnsMany listOf(requestedPrincipal.right(), requestedOf.right())
            val repository = mockk<DocumentRepository>()
            val creationHandler = CreateDocumentHandler(
                mockk(),
                mockk(),
                mockk(),
                pdfGenerator,
                pdfSigner,
                repository,
                mockk(),
            )

            testApplication {
                setupAppWith(requestedBy) {
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
                        "Only authenticated market entities can create documents of type 'ChangeOfEnergySupplierForOrganization'."
                    error["source"] shouldBe null
                } else {
                    response.status shouldBe HttpStatusCode.UnprocessableEntity
                    error.getValue("status").jsonPrimitive.content shouldBe "422"
                    error.getValue("title").jsonPrimitive.content shouldBe
                        if (role == "requestedPrincipal") "Invalid requestedPrincipal" else "Invalid requestedOf"
                    error.getValue("detail").jsonPrimitive.content shouldBe
                        "data.meta.$role must identify an organization for document type 'ChangeOfEnergySupplierForOrganization'."
                    error["source"] shouldBe null
                }
                coVerify(exactly = 0) { repository.insert(any()) }
            }
        }
    }

    test("POST / returns 201 and passes the authorized agent to the handler") {
        val document = responseDocument().copy(
            meta = Json.parseToJsonElement(
                """{"meteringPointId":"707057500000000001","nested":{"values":[null,false,1.5,"text"]},"placeholder":true}""",
            ).jsonObject,
        )
        coEvery {
            handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any())
        } returns document
        coEvery { agentService.resolve(any()) } returnsMany listOf(
            AuthorizationAgent("999888771", AgentType.Organization).right(),
            AuthorizationAgent("999888772", AgentType.Organization).right(),
        )

        testApplication {
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
            }

            val response = client.postJson("/", requestBody)

            response.status shouldBe HttpStatusCode.Created
            val body: JsonApiGetAuthorizationDocumentResponse = response.body()
            body.data.type shouldBe "AuthorizationDocument"
            body.data.meta shouldBe document.meta
            body.data.id shouldBe body.data.links.self.substringAfterLast('/')
            body.data.attributes.documentType shouldBe AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization
            body.data.attributes.status.name shouldBe "Pending"
            body.data.attributes.validFrom shouldBe Instant.parse("2026-10-01T08:00:00Z")
            body.data.attributes.updatedAt shouldBe Instant.fromEpochSeconds(document.updatedAt.epochSeconds)
            body.data.relationships.requestedBy.data.id shouldBe authorizedAgent.id
            body.data.relationships.requestedPrincipal.data.id shouldBe "999888771"
            body.data.relationships.requestedOf.data.id shouldBe "999888772"
            val returnedScope = body.data.attributes.requestedScopes.single()
            returnedScope.resourceType shouldBe ResourceType.MeteringPointContract
            returnedScope.appliesTo.single().attribute shouldBe "meteringPoint.id"
            returnedScope.appliesTo.single().value shouldBe listOf("707057500000000001")
            returnedScope.allowedChanges.single().attribute shouldBe "type"
            returnedScope.allowedChanges.single().value shouldBe listOf("EnergySupplier")
            coVerify(exactly = 1) {
                agentService.resolve(AgentExternalIdentifier.create("999888771").toPartyIdentifier())
            }
            coVerify(exactly = 1) {
                agentService.resolve(AgentExternalIdentifier.create("999888772").toPartyIdentifier())
            }
            coVerify(exactly = 1) {
                handler.createAuthorizationDocument(
                    specification = match {
                        it.documentType == AuthorizationDocumentType.ChangeOfEnergySupplierForOrganization
                    },
                    externalReference = "contract-123",
                    requestedBy = authorizedAgent,
                    requestedPrincipal = AuthorizationAgent("999888771", AgentType.Organization),
                    requestedOf = AuthorizationAgent("999888772", AgentType.Organization),
                    language = DocumentLanguage.NB,
                )
            }
        }
    }

    test("POST / creates a framework agreement without scopes and preserves future requested grant dates") {
        val requestedGrantValidFrom = Instant.parse("2098-01-01T00:00:00Z")
        val requestedGrantValidTo = Instant.parse("2099-01-01T00:00:00Z")
        val specification = DocumentSpecification.EnergySupplierFrameworkAgreement(requestedGrantValidFrom, requestedGrantValidTo)
        val document = responseDocument().copy(
            documentType = AuthorizationDocumentType.EnergySupplierFrameworkAgreement,
            requestedScopes = listOf(specification.toRequestedAuthorizationScope()),
            requestedGrantValidFrom = requestedGrantValidFrom,
            requestedGrantValidTo = requestedGrantValidTo,
        )
        coEvery {
            handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any())
        } returns document
        coEvery { agentService.resolve(any()) } returnsMany listOf(
            AuthorizationAgent("999888771", AgentType.Organization).right(),
            AuthorizationAgent("999888772", AgentType.Organization).right(),
        )
        val frameworkRequest = """
            {
              "data": {
                "type": "AuthorizationDocument",
                "attributes": {
                  "documentType": "EnergySupplierFrameworkAgreement",
                  "externalReference": "contract-123",
                  "requestedGrantValidFrom": "2098-01-01T01:00:00+01:00",
                  "requestedGrantValidTo": "2099-01-01T01:00:00+01:00"
                },
                "meta": {
                  "requestedPrincipal": "999888771",
                   "requestedOf": "999888772",
                  "language": "nb"
                }
              }
            }
        """.trimIndent()

        testApplication {
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
            }

            val response = client.postJson("/", frameworkRequest)

            response.status shouldBe HttpStatusCode.Created
            val body: JsonApiGetAuthorizationDocumentResponse = response.body()
            body.data.id shouldBe document.id
            body.data.type shouldBe "AuthorizationDocument"
            body.data.attributes.documentType shouldBe AuthorizationDocumentType.EnergySupplierFrameworkAgreement
            body.data.attributes.status.name shouldBe "Pending"
            body.data.attributes.requestedGrantValidFrom shouldBe requestedGrantValidFrom
            body.data.attributes.requestedGrantValidTo shouldBe requestedGrantValidTo
            val returnedScope = body.data.attributes.requestedScopes.single()
            returnedScope.resourceType shouldBe ResourceType.MeteringPointContract
            returnedScope.appliesTo shouldBe emptyList()
            returnedScope.allowedChanges.single().attribute shouldBe "type"
            returnedScope.allowedChanges.single().value.toSet() shouldBe setOf("EndUser", "EnergySupplier")
            coVerify(exactly = 1) {
                handler.createAuthorizationDocument(
                    specification = specification,
                    externalReference = "contract-123",
                    requestedBy = authorizedAgent,
                    requestedPrincipal = AuthorizationAgent("999888771", AgentType.Organization),
                    requestedOf = AuthorizationAgent("999888772", AgentType.Organization),
                    language = DocumentLanguage.NB,
                )
            }
        }
    }

    test("POST / defaults the document language to Norwegian Bokmål") {
        coEvery {
            handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any())
        } returns responseDocument()
        coEvery { agentService.resolve(any()) } returnsMany listOf(
            AuthorizationAgent("999888771", AgentType.Organization).right(),
            AuthorizationAgent("999888772", AgentType.Organization).right(),
        )

        testApplication {
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
            }

            val response = client.postJson("/", requestBody.replace(Regex(",\\s*\"language\": \"nb\""), ""))

            response.status shouldBe HttpStatusCode.Created
            val body: JsonApiGetAuthorizationDocumentResponse = response.body()
            body.data.meta shouldBe JsonObject(emptyMap())
            coVerify(exactly = 1) {
                handler.createAuthorizationDocument(
                    specification = any(),
                    externalReference = "contract-123",
                    requestedBy = authorizedAgent,
                    requestedPrincipal = AuthorizationAgent("999888771", AgentType.Organization),
                    requestedOf = AuthorizationAgent("999888772", AgentType.Organization),
                    language = DocumentLanguage.NB,
                )
            }
        }
    }

    test("POST / maps move-in validFrom input and infers the allowed contract types") {
        coEvery {
            handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any())
        } returns responseDocument()
        coEvery { agentService.resolve(any()) } returnsMany listOf(
            AuthorizationAgent("999888771", AgentType.Organization).right(),
            AuthorizationAgent("999888772", AgentType.Organization).right(),
        )

        val requestedScope = """
            "requestedScopes": [{
              "resourceType": "MeteringPointContract",
              "appliesTo": [{
                "attribute": "meteringPoint.id",
                "value": ["707057500000000001"]
              }],
              "allowedChanges": [{
                "attribute": "validFrom",
                "value": ["2026-10-01"]
              }]
            }]
        """.trimIndent()
        val moveInRequest = requestBody
            .replace("ChangeOfEnergySupplierForOrganization", "MoveInAndChangeOfEnergySupplierForOrganization")
            .replace("\"externalReference\": \"contract-123\"", "\"externalReference\": \"contract-123\",$requestedScope")
        val expectedSpecification = DocumentSpecification.MoveInAndChangeOfEnergySupplierForOrganization(
            meteringPointIds = ResourceConstraint.MeteringPoints(
                setOf(MeteringPointId.create("707057500000000001")),
            ),
            validFrom = LocalDate(2026, 10, 1),
        )

        testApplication {
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
            }

            val response = client.postJson("/", moveInRequest)

            response.status shouldBe HttpStatusCode.Created
            coVerify(exactly = 1) {
                handler.createAuthorizationDocument(
                    specification = expectedSpecification,
                    externalReference = "contract-123",
                    requestedBy = authorizedAgent,
                    requestedPrincipal = AuthorizationAgent("999888771", AgentType.Organization),
                    requestedOf = AuthorizationAgent("999888772", AgentType.Organization),
                    language = DocumentLanguage.NB,
                )
            }
        }
    }

    test("document service generates, signs and inserts the document and returns the saved document") {
        val changeSupplierHandler: ChangeOfEnergySupplierDocumentHandler = mockk()
        val moveInHandler: MoveInAndChangeOfEnergySupplierDocumentHandler = mockk()
        val documentRepository: DocumentRepository = mockk()
        val insertedDocument = slot<AuthorizationDocument>()
        val savedDocument = responseDocument()
        val specification = DocumentSpecification.ChangeOfEnergySupplierForOrganization(
            ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create("707057500000000001"))),
        )
        val pdfContent = mockk<no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent>()
        val unsignedPdf = byteArrayOf(1, 2)
        val signedPdf = byteArrayOf(3, 4)
        val validTo = Clock.System.now()
        val meta = JsonObject(mapOf("source" to kotlinx.serialization.json.JsonPrimitive("placeholder")))
        coEvery { changeSupplierHandler.prepare(specification, any()) } returns
            DocumentCreationDetails(pdfContent, validTo, meta)
        every { pdfGenerator.generate(pdfContent) } returns unsignedPdf
        coEvery { pdfSigner.sign(unsignedPdf) } returns signedPdf
        coEvery { documentRepository.insert(capture(insertedDocument)) } returns savedDocument

        val rulesService = mockk<AuthorizationRulesService>()
        coEvery { rulesService.canActOnBehalfOf(any(), any()) } returns true
        val service = CreateDocumentHandler(
            changeSupplierHandler,
            moveInHandler,
            mockk(),
            pdfGenerator,
            pdfSigner,
            documentRepository,
            rulesService,
        )
        val document = service.createAuthorizationDocument(
            specification = specification,
            externalReference = "ref",
            requestedBy = authorizedAgent,
            requestedPrincipal = AuthorizationAgent("org-1", AgentType.Organization),
            requestedOf = AuthorizationAgent("org-2", AgentType.Organization),
            language = DocumentLanguage.NB,
        )

        document shouldBe savedDocument
        insertedDocument.captured.apply {
            documentType shouldBe specification.documentType
            requestedScopes shouldBe listOf(specification.toRequestedAuthorizationScope())
            externalReference shouldBe "ref"
            requestedBy shouldBe authorizedAgent
            requestedPrincipal shouldBe AuthorizationAgent("org-1", AgentType.Organization)
            requestedOf shouldBe AuthorizationAgent("org-2", AgentType.Organization)
            status shouldBe AuthorizationDocumentStatus.Pending
            this.validTo shouldBe validTo
            validFrom shouldBe createdAt
            this.meta shouldBe meta
            pdfBytes.toList() shouldBe signedPdf.toList()
        }
        coVerify(exactly = 1) { documentRepository.insert(any()) }
        coVerify(exactly = 1) {
            rulesService.canActOnBehalfOf(
                AuthorizationAgent("org-2", AgentType.Organization),
                AuthorizationAgent("org-1", AgentType.Organization),
            )
        }
        coVerify(exactly = 0) { moveInHandler.prepare(any(), any()) }
    }

    test("document service does not generate or insert a document when metering point lookup fails") {
        val changeSupplierHandler: ChangeOfEnergySupplierDocumentHandler = mockk()
        val moveInHandler: MoveInAndChangeOfEnergySupplierDocumentHandler = mockk()
        val documentRepository: DocumentRepository = mockk()
        coEvery { changeSupplierHandler.prepare(any(), any()) } throws
            DocumentPreparationError.RequestedMeteringPointsNotFound()
        val rulesService = mockk<AuthorizationRulesService>()
        coEvery { rulesService.canActOnBehalfOf(any(), any()) } returns true
        val service = CreateDocumentHandler(
            changeSupplierHandler,
            moveInHandler,
            mockk(),
            pdfGenerator,
            pdfSigner,
            documentRepository,
            rulesService,
        )
        val specification = DocumentSpecification.ChangeOfEnergySupplierForOrganization(
            ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create("707057500000000001"))),
        )

        shouldThrow<DocumentPreparationError.RequestedMeteringPointsNotFound> {
            service.createAuthorizationDocument(
                specification = specification,
                externalReference = "ref",
                requestedBy = authorizedAgent,
                requestedPrincipal = AuthorizationAgent("org-1", AgentType.Organization),
                requestedOf = AuthorizationAgent("org-2", AgentType.Organization),
                language = DocumentLanguage.NB,
            )
        }
        coVerify(exactly = 0) { documentRepository.insert(any()) }
    }

    test("POST / returns 409 when the resource type is wrong") {
        testApplication {
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
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
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
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
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
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
            coVerify(exactly = 0) { agentService.resolve(any()) }
            coVerify(exactly = 0) { handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any()) }
        }
    }

    test("POST / returns 422 when move-in validFrom contains multiple dates") {
        testApplication {
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
            }

            val requestedScope = """
                "requestedScopes": [{
                  "resourceType": "MeteringPointContract",
                  "appliesTo": [{
                    "attribute": "meteringPoint.id",
                    "value": ["707057500000000001"]
                  }],
                  "allowedChanges": [{
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
            coVerify(exactly = 0) { agentService.resolve(any()) }
            coVerify(exactly = 0) {
                handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any())
            }
        }
    }

    test("POST / returns 422 when move-in explicitly supplies an allowed type change") {
        testApplication {
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
            }

            val requestedScope = """
                "requestedScopes": [{
                  "resourceType": "MeteringPointContract",
                  "appliesTo": [{
                    "attribute": "meteringPoint.id",
                    "value": ["707057500000000001"]
                  }],
                  "allowedChanges": [
                    { "attribute": "type", "value": ["EnergySupplier", "EndUser"] },
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
                "requestedScopes[0].allowedChanges must be empty or contain exactly one validFrom constraint"
        }
    }

    test("POST / returns 422 when a metering-point ID has an invalid format") {
        testApplication {
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
            }

            val response = client.postJson(
                "/",
                requestBody.replace("707057500000000001", "invalid"),
            )

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().detail shouldBe
                "requestedScopes[0].appliesTo meteringPoint.id values must be valid 18-digit metering-point IDs"
            coVerify(exactly = 0) { agentService.resolve(any()) }
            coVerify(exactly = 0) {
                handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any())
            }
        }
    }

    test("POST / returns 422 when requested scope has an unsupported resource type") {
        testApplication {
            setupAppWith(authorizedAgent) {
                route(agentService, handler)
            }

            val response = client.postJson(
                "/",
                requestBody.replace("MeteringPointContract", "OtherResource"),
            )

            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val body: JsonApiErrorCollection = response.body()
            body.errors.single().detail shouldBe
                "requestedScopes[0].resourceType must be 'MeteringPointContract'"
            coVerify(exactly = 0) { agentService.resolve(any()) }
            coVerify(exactly = 0) { handler.createAuthorizationDocument(any(), any(), any(), any(), any(), any()) }
        }
    }
})
