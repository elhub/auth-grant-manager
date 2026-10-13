package no.elhub.auth.v1.features.documents.create

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.LocalDate
import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyType
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.auth.v1.domain.MeteringPointId
import no.elhub.auth.v1.domain.ResourceConstraint
import kotlin.time.Clock

class PlaceholderDocumentActionHandlersTest : FunSpec({
    val context = DocumentActionContext(
        requestedBy = AuthorizationParty("supplier-1", PartyType.OrganizationEntity),
        requestedFrom = AuthorizationParty("org-1", PartyType.Organization),
        requestedTo = AuthorizationParty("person-1", PartyType.Person),
        language = DocumentLanguage.EN,
        externalReference = "reference-1",
    )
    val meteringPointIds = ResourceConstraint.MeteringPoints(
        setOf(MeteringPointId.create("707057500000000001")),
    )

    test("change-supplier placeholder returns document-specific details") {
        val details = PlaceholderChangeOfEnergySupplierDocumentActionHandler().validateAndGetDetails(
            action = DocumentAction.ChangeOfEnergySupplierForOrganization(meteringPointIds),
            context = context,
        )

        val pdfContent = details.pdfContent.shouldBeInstanceOf<AuthorizationDocumentPdfContent.ChangeOfBalanceSupplierForOrganisation>()
        pdfContent.language shouldBe no.elhub.auth.common.documents.pdf.PdfLanguage.EN
        pdfContent.meteringPointId shouldBe "707057500000000001"
        (details.validTo > Clock.System.now()) shouldBe true
        details.meta["placeholder"]?.toString() shouldBe "true"
    }

    test("move-in placeholder returns document-specific details including move-in date") {
        val validFrom = LocalDate(2026, 10, 1)
        val details = PlaceholderMoveInAndChangeOfEnergySupplierDocumentActionHandler().validateAndGetDetails(
            action = DocumentAction.MoveInAndChangeOfEnergySupplierForOrganization(
                meteringPointIds = meteringPointIds,
                validFrom = validFrom,
            ),
            context = context,
        )

        val pdfContent = details.pdfContent.shouldBeInstanceOf<AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplierForOrganisation>()
        pdfContent.language shouldBe no.elhub.auth.common.documents.pdf.PdfLanguage.EN
        pdfContent.meteringPointId shouldBe "707057500000000001"
        pdfContent.moveInDate shouldBe validFrom
        (details.validTo > Clock.System.now()) shouldBe true
        details.meta["validFrom"]?.toString() shouldBe "\"2026-10-01\""
    }
})
