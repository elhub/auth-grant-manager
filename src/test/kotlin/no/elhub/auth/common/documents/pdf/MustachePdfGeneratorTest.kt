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
    test("generates change of supplier PDF directly from common content") {
        val pdf = generator(useTestPdfNotice = false).generate(
            AuthorizationDocumentPdfContent.ChangeOfBalanceSupplier(
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
        pdfLanguage(pdf) shouldBe "en-US"
        pdfAuthor(pdf) shouldBe "Elhub AS"
    }

    test("generates move-in PDF and formats move-in date") {
        val pdf = generator(useTestPdfNotice = false).generate(
            AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplier(
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
        pdfLanguage(pdf) shouldBe "nb-NO"
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

private fun changeOfSupplierContent() = AuthorizationDocumentPdfContent.ChangeOfBalanceSupplier(
    language = PdfLanguage.NB,
    customerName = "Requester",
    meteringPointAddress = "Address 1",
    meteringPointId = "Meter123",
    meterNumber = "123456789",
    balanceSupplierName = "Balance Supplier",
    balanceSupplierContractName = "Contract Name",
)

private fun pdfText(pdf: ByteArray): String =
    Loader.loadPDF(pdf).use { PDFTextStripper().getText(it) }

private fun pdfLanguage(pdf: ByteArray): String? =
    Loader.loadPDF(pdf).use { it.documentCatalog.language }

private fun pdfAuthor(pdf: ByteArray): String? =
    Loader.loadPDF(pdf).use { it.documentInformation.author }

private fun pdfCustomMetadata(pdf: ByteArray, key: String): String? =
    Loader.loadPDF(pdf).use { it.documentInformation.getCustomMetadataValue(key) }
