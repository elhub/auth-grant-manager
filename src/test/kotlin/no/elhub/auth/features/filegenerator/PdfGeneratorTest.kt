package no.elhub.auth.features.filegenerator

import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.datetime.LocalDate
import no.elhub.auth.features.businessprocesses.changeofsupplier.domain.ChangeOfSupplierBusinessMeta
import no.elhub.auth.features.businessprocesses.movein.domain.MoveInBusinessMeta
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

class PdfGeneratorTest :
    FunSpec({
        val cosMeta = ChangeOfSupplierBusinessMeta(
            balanceSupplierName = "Balance Supplier",
            balanceSupplierContractName = "Contract Name",
            requestedForMeteringPointId = "Meter123",
            requestedForMeteringPointAddress = "Address 1",
            requestedFromName = "Requester"
        )
        val moveInMeta = MoveInBusinessMeta(
            requestedFromName = "Alberto Balsalm",
            requestedForMeteringPointId = "Meter123",
            requestedForMeteringPointAddress = "Address 1",
            balanceSupplierContractName = "Contract Name",
            balanceSupplierName = "Greatest Balance Supplier of all",
            startDate = LocalDate(2024, 1, 1),
            redirectURI = "https://example.com",
        )

        test("Generates PDF with watermark and correct metadata for change of supplier meta") {
            val cfg = PdfGeneratorConfig(
                mustacheResourcePath = "templates",
                useTestPdfNotice = true
            )
            val pdfGenerator = PdfGenerator(cfg)

            val result = pdfGenerator.generate(
                signerNin = "123",
                documentMeta = cosMeta
            )
            result.shouldBeRight()

            val text = extractText(result.value)
            text shouldContain "TESTDOKUMENT"
            text shouldContain "Balance Supplier"
            Loader.loadPDF(result.value).use { document ->
                val metadata = document.documentInformation
                val signerNin = metadata.getCustomMetadataValue("signerNin")
                val testDocument = metadata.getCustomMetadataValue("testDocument")

                signerNin shouldBe "123"
                testDocument shouldBe "true"
            }
        }

        test("Generates PDF without watermark and correct metadata for change of supplier metadata") {
            val cfg = PdfGeneratorConfig(
                mustacheResourcePath = "templates",
                useTestPdfNotice = false
            )
            val pdfGenerator = PdfGenerator(cfg)

            val result = pdfGenerator.generate(
                signerNin = "123",
                documentMeta = cosMeta
            )

            result.shouldBeRight()
            val text = extractText(result.value)
            text shouldNotContain "TESTDOKUMENT"
            text shouldContain "Balance Supplier"
            Loader.loadPDF(result.value).use { document ->
                val metadata = document.documentInformation
                val signerNin = metadata.getCustomMetadataValue("signerNin")
                val testDocument = metadata.getCustomMetadataValue("testDocument")

                signerNin shouldBe "123"
                testDocument.shouldBeNull()
            }
        }
        test("Generates PDF with watermark and correct metadata for move in meta") {
            val cfg = PdfGeneratorConfig(
                mustacheResourcePath = "templates",
                useTestPdfNotice = true
            )
            val pdfGenerator = PdfGenerator(cfg)

            val result = pdfGenerator.generate(
                signerNin = "123",
                documentMeta = moveInMeta
            )
            result.shouldBeRight()

            val text = extractText(result.value)
            text shouldContain "TESTDOKUMENT"
            text shouldContain "Balance Supplier"

            Loader.loadPDF(result.value).use { document ->
                val metadata = document.documentInformation
                val signerNin = metadata.getCustomMetadataValue("signerNin")
                val testDocument = metadata.getCustomMetadataValue("testDocument")

                signerNin shouldBe "123"
                testDocument shouldBe "true"
            }
        }
        test("Generates PDF without watermark and correct metadata for move in and change of supplier meta") {
            val cfg = PdfGeneratorConfig(
                mustacheResourcePath = "templates",
                useTestPdfNotice = false
            )
            val pdfGenerator = PdfGenerator(cfg)

            val result = pdfGenerator.generate(
                signerNin = "123",
                documentMeta = moveInMeta
            )

            result.shouldBeRight()
            val text = extractText(result.value)
            text shouldNotContain "TESTDOKUMENT"
            text shouldContain "Balance Supplier"

            Loader.loadPDF(result.value).use { document ->
                val metadata = document.documentInformation
                val signerNin = metadata.getCustomMetadataValue("signerNin")
                val testDocument = metadata.getCustomMetadataValue("testDocument")

                signerNin shouldBe "123"
                testDocument.shouldBeNull()
            }
        }
    })

fun extractText(pdf: ByteArray): String =
    Loader.loadPDF(pdf).use { document ->
        PDFTextStripper().getText(document)
    }
