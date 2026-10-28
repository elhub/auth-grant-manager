package no.elhub.auth.v1.documenthandlers.supplierchange

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.creation.DocumentCreationContext
import kotlin.time.Clock

class ChangeOfEnergySupplierDocumentHandlerTest : FunSpec({
    val context = DocumentCreationContext(
        requestedBy = AuthorizationParty("supplier-1", PartyType.OrganizationEntity),
        requestedPrincipal = AuthorizationParty("org-1", PartyType.Organization),
        requestedOf = AuthorizationParty("person-1", PartyType.Person),
        language = DocumentLanguage.EN,
        externalReference = "reference-1",
    )
    val meteringPointIds = ResourceConstraint.MeteringPoints(
        setOf(MeteringPointId.create("707057500000000001")),
    )

    test("prepares supplier-change document details") {
        val details = ChangeOfEnergySupplierDocumentHandler().prepare(
            specification = DocumentSpecification.ChangeOfEnergySupplierForOrganization(meteringPointIds),
            context = context,
        )

        val pdfContent = details.pdfContent.shouldBeInstanceOf<AuthorizationDocumentPdfContent.ChangeOfBalanceSupplierForOrganisation>()
        pdfContent.language shouldBe no.elhub.auth.common.documents.pdf.PdfLanguage.EN
        pdfContent.meteringPointId shouldBe "707057500000000001"
        (details.validTo > Clock.System.now()) shouldBe true
        details.meta["placeholder"]?.toString() shouldBe "true"
    }
})
