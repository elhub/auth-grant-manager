package no.elhub.auth.common.documents.pdf

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.datetime.LocalDate
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

class MustachePdfGeneratorTest : FunSpec({
    test("generates person and organization PDFs through the shared change-of-supplier model") {
        val content: List<AuthorizationDocumentPdfContent.ChangeOfBalanceSupplier> =
            listOf(
                changeOfSupplierContent(),
                organizationChangeOfSupplierContent(),
                organizationMoveInContent(PdfLanguage.NB, LocalDate(2026, 5, 1)),
            )

        content.map { generator(useTestPdfNotice = false).generate(it) }.size shouldBe 3
    }

    test("generates change of supplier PDF directly from common content") {
        val pdf = generator(useTestPdfNotice = false).generate(
            AuthorizationDocumentPdfContent.ChangeOfBalanceSupplierForPerson(
                language = PdfLanguage.EN,
                customerName = "Hillary Orr",
                meteringPointAddress = "Example Street 1, 1234 Oslo",
                meteringPointId = "123456789012345678",
                meterNumber = "123456789",
                balanceSupplierName = "Jami Wade",
                balanceSupplierContractName = "Selena Chandler",
            )
        )

        pdfText(pdf) shouldContain "Hillary Orr"
        pdfText(pdf) shouldContain "Confirm electricity supply agreement"
        pdfText(pdf) shouldContain "you confirm that you have accepted"
        pdfText(pdf) shouldContain "agreement referenced above."
        pdfText(pdf) shouldContain "Electricity supply agreement: Selena Chandler"
        pdfText(pdf) shouldNotContain "on behalf of the organization"
        pdfLanguage(pdf) shouldBe "en-US"
        pdfAuthor(pdf) shouldBe "Elhub AS"
    }

    test("generates move-in PDF and formats move-in date") {
        val pdf = generator(useTestPdfNotice = false).generate(
            AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplierForPerson(
                language = PdfLanguage.NB,
                customerName = "Alberto Balsalm",
                meteringPointAddress = "Address 1",
                meteringPointId = "Meter123",
                meterNumber = "123456789",
                balanceSupplierName = "Greatest Balance Supplier of all",
                balanceSupplierContractName = "Contract Name",
                moveInDate = LocalDate(2024, 1, 1),
            )
        )

        val text = pdfText(pdf)
        text shouldContain "Alberto Balsalm"
        text shouldContain "01.01.2024"
        text shouldContain "Greatest Balance Supplier of all"
        text shouldContain "Ved å signere dette dokumentet bekrefter du at du har inngått strømavtalen"
        text shouldNotContain "på vegne av organisasjonen"
        pdfLanguage(pdf) shouldBe "nb-NO"
    }

    test("preserves the production hyphen in every person move-in title") {
        val expectedTitles =
            mapOf(
                PdfLanguage.NB to "Avtalebekreftelse - Innflytting og leverandørskifte",
                PdfLanguage.NN to "Stadfest straumavtale - Innflytting og leverandørskifte",
                PdfLanguage.EN to "Confirm electricity supply agreement - Move-in and change of supplier",
            )

        expectedTitles.forEach { (language, title) ->
            val pdf = generator(useTestPdfNotice = false).generate(personMoveInContent(language))

            pdfText(pdf) shouldContain title
        }
    }

    test("generates a change-of-supplier PDF with organization signing confirmation") {
        val pdf = generator(useTestPdfNotice = false).generate(
            organizationChangeOfSupplierContent()
        )

        val text = pdfText(pdf)
        text shouldContain "Avtalebekreftelse - Leverandørskifte"
        text shouldContain "Navn AS"
        text shouldContain "Organisasjonsnummer: 100 010 001"
        text shouldContain "Bjørkeveien 18C, 0168 Oslo"
        text shouldContain "Norgesstrøm"
        text shouldContain "Avtalereferanse: ABC123"
        text shouldContain "på vegne av organisasjonen"
        text shouldContain "nødvendig fullmakt"
    }

    test("generates a move-in PDF for an organization with a localized date") {
        val pdf = generator(useTestPdfNotice = false).generate(
            organizationMoveInContent(PdfLanguage.NB, LocalDate(2026, 5, 1))
        )

        val text = pdfText(pdf)
        text shouldContain "Avtalebekreftelse - Innflytting og leverandørskifte"
        text shouldContain "Kunde: Navn AS"
        text shouldContain "Organisasjonsnummer: 100 010 001"
        text shouldContain "Avtalereferanse: ABC123"
        text shouldContain "Innflyttingsdato: 01.05.2026"
        text shouldContain "på vegne av organisasjonen"
        text shouldContain "må forespørselen bekreftes innen"
        text shouldContain "4 uker."
        text shouldContain "Dette dokumentet vil være tilgjengelig for strømkunden på Elhub Min Side."
        text shouldContain "finner du på Elhub sin hjemmeside."
        pdfLanguage(pdf) shouldBe "nb-NO"
    }

    test("omits an absent agreement reference from organization PDFs") {
        val changeOfSupplierPdf =
            generator(useTestPdfNotice = false).generate(
                organizationChangeOfSupplierContent().copy(agreementReference = null)
            )
        val moveInPdf =
            generator(useTestPdfNotice = false).generate(
                organizationMoveInContent(PdfLanguage.NB, null).copy(agreementReference = null)
            )

        listOf(changeOfSupplierPdf, moveInPdf).forEach { pdf ->
            val text = pdfText(pdf)
            text shouldNotContain "Avtalereferanse:"
            text shouldNotContain "som det vises til over"
            text shouldContain "har inngått en strømavtale"
        }
    }

    test("uses the same numeric move-in date format for organization PDFs in English") {
        val pdf = generator(useTestPdfNotice = false).generate(
            organizationMoveInContent(PdfLanguage.EN, LocalDate(2026, 5, 1))
        )

        pdfText(pdf) shouldContain "Move-in date: 01.05.2026"
    }

    test("uses the same numeric move-in date format for organization PDFs in Nynorsk") {
        val pdf = generator(useTestPdfNotice = false).generate(
            organizationMoveInContent(PdfLanguage.NN, LocalDate(2026, 5, 1))
        )

        pdfText(pdf) shouldContain "Innflyttingsdato: 01.05.2026"
    }

    test("generates a localized framework agreement PDF with an end date") {
        val pdf = generator(useTestPdfNotice = false).generate(
            AuthorizationDocumentPdfContent.FrameworkAgreement(
                language = PdfLanguage.EN,
                organizationName = "Navn AS",
                organizationNumber = "100 010 001",
                balanceSupplierName = "Elvekraft",
                agreementReference = "Elvekraft Framework Agreement ABC213",
                startDate = LocalDate(2027, 1, 1),
                endDate = LocalDate(2029, 12, 31),
            )
        )

        val text = pdfText(pdf)
        text shouldContain "Framework agreement confirmation"
        text shouldContain "Navn AS"
        text shouldContain "100 010 001"
        text shouldContain "Elvekraft Framework Agreement ABC213"
        text shouldContain "Electricity supply agreement: Elvekraft Framework Agreement ABC213"
        text shouldContain "01. January 2027"
        text shouldContain "31. December 2029"
        pdfLanguage(pdf) shouldBe "en-US"
    }

    test("renders ongoing framework agreement when the end date is absent") {
        val pdf = generator(useTestPdfNotice = false).generate(
            AuthorizationDocumentPdfContent.FrameworkAgreement(
                language = PdfLanguage.NB,
                organizationName = "Navn AS",
                organizationNumber = "100 010 001",
                balanceSupplierName = "Elvekraft",
                agreementReference = "Elvekraft Rammeavtale ABC213",
                startDate = LocalDate(2027, 1, 1),
                endDate = null,
            )
        )

        val text = pdfText(pdf)
        text shouldContain "01. januar 2027"
        text shouldContain "Løpende avtale"
        pdfLanguage(pdf) shouldBe "nb-NO"
    }

    test("formats framework agreement dates using the Nynorsk locale") {
        val pdf = generator(useTestPdfNotice = false).generate(
            AuthorizationDocumentPdfContent.FrameworkAgreement(
                language = PdfLanguage.NN,
                organizationName = "Navn AS",
                organizationNumber = "100 010 001",
                balanceSupplierName = "Elvekraft",
                agreementReference = "Elvekraft Rammeavtale ABC213",
                startDate = LocalDate(2027, 1, 1),
                endDate = LocalDate(2029, 12, 31),
            )
        )

        val text = pdfText(pdf)
        text shouldContain "01. januar 2027"
        text shouldContain "31. desember 2029"
        pdfLanguage(pdf) shouldBe "nn-NO"
    }

    test("adds test watermark and metadata when configured") {
        val pdf = generator(useTestPdfNotice = true).generate(changeOfSupplierContent())

        pdfText(pdf) shouldContain "TESTDOKUMENT"
        pdfCustomMetadata(pdf, "testDocument") shouldBe "true"
    }

    test("does not add test watermark or metadata when disabled") {
        val pdf = generator(useTestPdfNotice = false).generate(changeOfSupplierContent())

        pdfText(pdf) shouldNotContain "TESTDOKUMENT"
        pdfCustomMetadata(pdf, "testDocument").shouldBeNull()
    }
})

private fun generator(useTestPdfNotice: Boolean) = MustachePdfGenerator(
    PdfGeneratorConfig(
        mustacheResourcePath = "templates",
        useTestPdfNotice = useTestPdfNotice,
    )
)

private fun changeOfSupplierContent() = AuthorizationDocumentPdfContent.ChangeOfBalanceSupplierForPerson(
    language = PdfLanguage.NB,
    customerName = "Requester",
    meteringPointAddress = "Address 1",
    meteringPointId = "Meter123",
    meterNumber = "123456789",
    balanceSupplierName = "Balance Supplier",
    balanceSupplierContractName = "Contract Name",
)

private fun personMoveInContent(language: PdfLanguage) =
    AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplierForPerson(
        language = language,
        customerName = "Customer",
        meteringPointAddress = "Address 1",
        meteringPointId = "Meter123",
        meterNumber = "123456789",
        balanceSupplierName = "Balance Supplier",
        balanceSupplierContractName = "Contract Name",
        moveInDate = LocalDate(2026, 5, 1),
    )

private fun organizationChangeOfSupplierContent(
    language: PdfLanguage = PdfLanguage.NB,
) = AuthorizationDocumentPdfContent.ChangeOfBalanceSupplierForOrganisation(
    language = language,
    organizationName = "Navn AS",
    organizationNumber = "100 010 001",
    meteringPointAddress = "Bjørkeveien 18C, 0168 Oslo",
    meteringPointId = "707057500047917289",
    meterNumber = "57390234",
    balanceSupplierName = "Norgesstrøm",
    agreementReference = "ABC123",
)

private fun organizationMoveInContent(
    language: PdfLanguage,
    moveInDate: LocalDate?,
) = AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplierForOrganisation(
    language = language,
    organizationName = "Navn AS",
    organizationNumber = "100 010 001",
    meteringPointAddress = "Bjørkeveien 18C, 0168 Oslo",
    meteringPointId = "707057500047917289",
    meterNumber = "57390234",
    balanceSupplierName = "Norgesstrøm",
    agreementReference = "ABC123",
    moveInDate = moveInDate,
)

private fun pdfText(pdf: ByteArray): String =
    Loader.loadPDF(pdf).use { PDFTextStripper().getText(it) }

private fun pdfLanguage(pdf: ByteArray): String? =
    Loader.loadPDF(pdf).use { it.documentCatalog.language }

private fun pdfAuthor(pdf: ByteArray): String? =
    Loader.loadPDF(pdf).use { it.documentInformation.author }

private fun pdfCustomMetadata(pdf: ByteArray, key: String): String? =
    Loader.loadPDF(pdf).use { it.documentInformation.getCustomMetadataValue(key) }
