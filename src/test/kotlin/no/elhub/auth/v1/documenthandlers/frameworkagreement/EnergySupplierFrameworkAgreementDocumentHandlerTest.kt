package no.elhub.auth.v1.documenthandlers.frameworkagreement

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.common.documents.pdf.PdfGenerator
import no.elhub.auth.common.documents.pdf.PdfLanguage
import no.elhub.auth.common.documents.pdf.PdfSigner
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.CreateDocumentHandler
import no.elhub.auth.v1.features.documents.creation.ChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.features.documents.creation.DocumentCreationContext
import no.elhub.auth.v1.features.documents.creation.MoveInAndChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.repositories.DocumentRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import no.elhub.auth.v1.features.documents.creation.EnergySupplierFrameworkAgreementDocumentHandler as HandlerContract

class EnergySupplierFrameworkAgreementDocumentHandlerTest : FunSpec({
    val context = DocumentCreationContext(
        requestedBy = AuthorizationParty("supplier-1", PartyType.OrganizationEntity),
        requestedPrincipal = AuthorizationParty("org-1", PartyType.Organization),
        requestedOf = AuthorizationParty("person-1", PartyType.Person),
        language = DocumentLanguage.EN,
        externalReference = "reference-1",
    )
    val validFrom = Instant.parse("2100-01-01T23:30:00Z")
    val validTo = Instant.parse("2100-06-01T22:30:00Z")
    val handler = EnergySupplierFrameworkAgreementDocumentHandler()

    test("prepares framework PDF content with Oslo dates and a 28-day document lifetime") {
        val before = Clock.System.now()
        val details = handler.prepare(DocumentSpecification.EnergySupplierFrameworkAgreement(validFrom, validTo), context)
        val after = Clock.System.now()

        val content = details.pdfContent.shouldBeInstanceOf<AuthorizationDocumentPdfContent.EnergySupplierFrameworkAgreement>()
        content.language shouldBe PdfLanguage.EN
        content.organizationName shouldBe "Placeholder organization org-1"
        content.organizationNumber shouldBe "org-1"
        content.balanceSupplierName shouldBe "Placeholder supplier supplier-1"
        content.agreementReference shouldBe "reference-1"
        content.startDate shouldBe LocalDate(2100, 1, 2)
        content.endDate shouldBe LocalDate(2100, 6, 2)
        (details.validTo >= before + 28.days) shouldBe true
        (details.validTo <= after + 28.days) shouldBe true
        details.meta["placeholder"]?.toString() shouldBe "true"
    }

    test("uses today's Oslo date and an explicit reference placeholder when omitted, with an open end") {
        val timeZone = TimeZone.of("Europe/Oslo")
        val before = Clock.System.now().toLocalDateTime(timeZone).date
        val details = handler.prepare(
            DocumentSpecification.EnergySupplierFrameworkAgreement(null, null),
            context.copy(externalReference = null),
        )
        val after = Clock.System.now().toLocalDateTime(timeZone).date

        val content = details.pdfContent.shouldBeInstanceOf<AuthorizationDocumentPdfContent.EnergySupplierFrameworkAgreement>()
        (content.startDate in setOf(before, after)) shouldBe true
        content.endDate shouldBe null
        content.agreementReference shouldBe "Placeholder agreement reference"
    }

    test("keeps an explicit start date when the agreement has no end date") {
        val details = handler.prepare(DocumentSpecification.EnergySupplierFrameworkAgreement(validFrom, null), context)

        val content = details.pdfContent.shouldBeInstanceOf<AuthorizationDocumentPdfContent.EnergySupplierFrameworkAgreement>()
        content.startDate shouldBe LocalDate(2100, 1, 2)
        content.endDate shouldBe null
    }

    listOf(validFrom to validTo, validFrom to null, null to validTo, null to null).forEach { (start, end) ->
        test("dispatches framework creation and persists signed PDF and requested dates $start to $end") {
            val specification = DocumentSpecification.EnergySupplierFrameworkAgreement(start, end)
            val details = handler.prepare(specification, context)
            val frameworkHandler = mockk<HandlerContract>()
            val supplierChangeHandler = mockk<ChangeOfEnergySupplierDocumentHandler>()
            val moveInHandler = mockk<MoveInAndChangeOfEnergySupplierDocumentHandler>()
            val pdfGenerator = mockk<PdfGenerator>()
            val pdfSigner = mockk<PdfSigner>()
            val repository = mockk<DocumentRepository>()
            val inserted = slot<AuthorizationDocument>()
            val unsignedPdf = byteArrayOf(1, 2)
            val signedPdf = byteArrayOf(3, 4)
            coEvery { frameworkHandler.prepare(specification, context) } returns details
            every { pdfGenerator.generate(details.pdfContent) } returns unsignedPdf
            coEvery { pdfSigner.sign(unsignedPdf) } returns signedPdf
            coEvery { repository.insert(capture(inserted)) } answers { inserted.captured.copy(id = "saved-id") }

            val saved = CreateDocumentHandler(
                supplierChangeHandler,
                moveInHandler,
                frameworkHandler,
                pdfGenerator,
                pdfSigner,
                repository,
            ).createAuthorizationDocument(
                specification = specification,
                externalReference = context.externalReference,
                requestedBy = context.requestedBy,
                requestedPrincipal = context.requestedPrincipal,
                requestedOf = context.requestedOf,
                language = context.language,
            )

            saved shouldBe inserted.captured.copy(id = "saved-id")
            inserted.captured.apply {
                documentType shouldBe AuthorizationDocumentType.EnergySupplierFrameworkAgreement
                status shouldBe AuthorizationDocumentStatus.Pending
                requestedScopes shouldBe listOf(specification.toRequestedAuthorizationScope())
                requestedGrantValidFrom shouldBe start
                requestedGrantValidTo shouldBe end
                externalReference shouldBe context.externalReference
                requestedBy shouldBe context.requestedBy
                requestedPrincipal shouldBe context.requestedPrincipal
                requestedOf shouldBe context.requestedOf
                this.validTo shouldBe details.validTo
                meta shouldBe details.meta
                pdfBytes.toList() shouldBe signedPdf.toList()
            }
            coVerify(exactly = 1) { frameworkHandler.prepare(specification, context) }
            coVerify(exactly = 1) { repository.insert(any()) }
            coVerify(exactly = 0) { supplierChangeHandler.prepare(any(), any()) }
            coVerify(exactly = 0) { moveInHandler.prepare(any(), any()) }
        }
    }
})
