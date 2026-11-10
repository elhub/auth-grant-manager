package no.elhub.auth.v1.features.documents

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.common.documents.pdf.PdfGenerator
import no.elhub.auth.common.documents.pdf.PdfSigner
import no.elhub.auth.v1.api.common.errors.InputError
import no.elhub.auth.v1.common.AuthorizationRulesService
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.creation.ChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.features.documents.creation.DocumentCreationAgentError
import no.elhub.auth.v1.features.documents.creation.DocumentCreationDetails
import no.elhub.auth.v1.features.documents.creation.EnergySupplierFrameworkAgreementDocumentHandler
import no.elhub.auth.v1.features.documents.creation.MoveInAndChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.repositories.DocumentRepository
import kotlin.time.Instant

class CreateDocumentHandlerTest : FunSpec({
    val roles = mapOf(
        "requestedBy" to AuthorizationAgent("supplier-1", AgentType.OrganizationEntity),
        "requestedPrincipal" to AuthorizationAgent("org-1", AgentType.Organization),
        "requestedOf" to AuthorizationAgent("org-2", AgentType.Organization),
    )
    val meteringPoints = ResourceConstraint.MeteringPoints(setOf(MeteringPointId.create("707057500000000001")))
    val specifications = listOf(
        DocumentSpecification.ChangeOfEnergySupplierForOrganization(meteringPoints),
        DocumentSpecification.MoveInAndChangeOfEnergySupplierForOrganization(meteringPoints, LocalDate(2026, 10, 1)),
        DocumentSpecification.EnergySupplierFrameworkAgreement(),
    )

    lateinit var supplierChangeHandler: ChangeOfEnergySupplierDocumentHandler
    lateinit var moveInHandler: MoveInAndChangeOfEnergySupplierDocumentHandler
    lateinit var frameworkAgreementHandler: EnergySupplierFrameworkAgreementDocumentHandler
    lateinit var pdfGenerator: PdfGenerator
    lateinit var pdfSigner: PdfSigner
    lateinit var repository: DocumentRepository
    lateinit var authorizationRulesService: AuthorizationRulesService
    lateinit var handler: CreateDocumentHandler

    beforeTest {
        supplierChangeHandler = mockk()
        moveInHandler = mockk()
        frameworkAgreementHandler = mockk()
        pdfGenerator = mockk()
        pdfSigner = mockk()
        repository = mockk()
        authorizationRulesService = mockk()
        coEvery { authorizationRulesService.canActOnBehalfOf(any(), any()) } returns true
        handler = CreateDocumentHandler(
            supplierChangeHandler,
            moveInHandler,
            frameworkAgreementHandler,
            pdfGenerator,
            pdfSigner,
            repository,
            authorizationRulesService,
        )
    }

    specifications.forEach { specification ->
        test("${specification.documentType} accepts organizations and a market entity") {
            val details = DocumentCreationDetails(mockk(), Instant.parse("2100-01-01T00:00:00Z"), JsonObject(emptyMap()))
            coEvery { supplierChangeHandler.prepare(any(), any()) } returns details
            coEvery { moveInHandler.prepare(any(), any()) } returns details
            coEvery { frameworkAgreementHandler.prepare(any(), any()) } returns details
            every { pdfGenerator.generate(details.pdfContent) } returns byteArrayOf(1)
            coEvery { pdfSigner.sign(any()) } returns byteArrayOf(2)
            coEvery { repository.insert(any()) } answers { firstArg() }

            val document = handler.createAuthorizationDocument(
                specification = specification,
                externalReference = null,
                requestedBy = roles.getValue("requestedBy"),
                requestedPrincipal = roles.getValue("requestedPrincipal"),
                requestedOf = roles.getValue("requestedOf"),
                language = DocumentLanguage.NB,
            )

            document.documentType shouldBe specification.documentType
            document.requestedBy shouldBe roles.getValue("requestedBy")
            document.requestedPrincipal shouldBe roles.getValue("requestedPrincipal")
            document.requestedOf shouldBe roles.getValue("requestedOf")
            document.pdfBytes.toList() shouldBe listOf<Byte>(2)
            coVerify(exactly = 1) { repository.insert(document) }
        }

        roles.forEach { (role, validAgent) ->
            AgentType.entries.filter { it != validAgent.type }.forEach { invalidType ->
                test("${specification.documentType} rejects $invalidType for $role before preparation, PDF creation or persistence") {
                    val agents = roles + (role to validAgent.copy(type = invalidType))
                    val error = shouldThrow<DocumentCreationAgentError> {
                        handler.createAuthorizationDocument(
                            specification = specification,
                            externalReference = null,
                            requestedBy = agents.getValue("requestedBy"),
                            requestedPrincipal = agents.getValue("requestedPrincipal"),
                            requestedOf = agents.getValue("requestedOf"),
                            language = DocumentLanguage.NB,
                        )
                    }

                    when (role) {
                        "requestedBy" -> error.shouldBeInstanceOf<DocumentCreationAgentError.RequestedByNotAllowed>()
                        "requestedOf" -> error.shouldBeInstanceOf<DocumentCreationAgentError.RequestedOfNotAllowed>()
                        "requestedPrincipal" -> error.shouldBeInstanceOf<DocumentCreationAgentError.RequestedPrincipalNotAllowed>()
                    }
                    error.documentType shouldBe specification.documentType
                    error.actualAgentType shouldBe invalidType
                    error.expectedAgentType shouldBe validAgent.type
                    coVerify(exactly = 0) {
                        authorizationRulesService.canActOnBehalfOf(any(), any())
                        supplierChangeHandler.prepare(any(), any())
                        moveInHandler.prepare(any(), any())
                        frameworkAgreementHandler.prepare(any(), any())
                        pdfSigner.sign(any())
                        repository.insert(any())
                    }
                    verify(exactly = 0) { pdfGenerator.generate(any()) }
                }
            }
        }
    }

    test("rejects unauthorized representatives before document preparation or persistence") {
        coEvery { authorizationRulesService.canActOnBehalfOf(any(), any()) } returns false

        shouldThrow<InputError.RequestedOfRequestedPrincipalMismatch> {
            handler.createAuthorizationDocument(
                specification = specifications.first(),
                externalReference = null,
                requestedBy = roles.getValue("requestedBy"),
                requestedPrincipal = roles.getValue("requestedPrincipal"),
                requestedOf = roles.getValue("requestedOf"),
                language = DocumentLanguage.NB,
            )
        }

        coVerify(exactly = 1) {
            authorizationRulesService.canActOnBehalfOf(
                roles.getValue("requestedOf"),
                roles.getValue("requestedPrincipal"),
            )
        }
        coVerify(exactly = 0) {
            supplierChangeHandler.prepare(any(), any())
            moveInHandler.prepare(any(), any())
            frameworkAgreementHandler.prepare(any(), any())
            repository.insert(any())
        }
        verify(exactly = 0) { pdfGenerator.generate(any()) }
    }
})
