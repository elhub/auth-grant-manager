package no.elhub.auth.features.filegenerator

import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.datetime.LocalDate
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.domain.ChangeOfBalanceSupplierBusinessMeta
import no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier.domain.MoveInAndChangeOfBalanceSupplierBusinessMeta
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import org.apache.pdfbox.text.PDFTextStripper

class PdfGeneratorTest :
    FunSpec({
        val cosMeta = ChangeOfBalanceSupplierBusinessMeta(
            language = SupportedLanguage.DEFAULT,
            balanceSupplierName = "Balance Supplier",
            balanceSupplierContractName = "Contract Name",
            requestedForMeteringPointId = "Meter123",
            requestedForMeterNumber = "123456789",
            requestedForMeteringPointAddress = "Address 1",
            requestedFromName = "Requester"
        )
        val moveInMeta = MoveInAndChangeOfBalanceSupplierBusinessMeta(
            language = SupportedLanguage.DEFAULT,
            requestedFromName = "Alberto Balsalm",
            requestedForMeteringPointId = "Meter123",
            requestedForMeterNumber = "123456789",
            requestedForMeteringPointAddress = "Address 1",
            balanceSupplierContractName = "Contract Name",
            balanceSupplierName = "Greatest Balance Supplier of all",
            moveInDate = LocalDate(2024, 1, 1),
            redirectURI = "https://example.com",
        )

        test("Generates PDF with correct language, author and producer metadata") {
            val cfg = PdfGeneratorConfig(
                mustacheResourcePath = "templates",
                useTestPdfNotice = false,
            )
            val pdfGenerator = PdfGenerator(cfg)

            val result = pdfGenerator.generate(
                documentMeta = cosMeta,
            )
            result.shouldBeRight()

            extractLanguage(result.value) shouldBe "nb-NO"
            val metadata = extractMetadata(result.value)
            metadata.author shouldBe "Elhub AS"
            metadata.producer shouldBe "Elhub Document Service"
        }

        test("Generates PDF with watermark and metadata for change of supplier meta") {
            val cfg = PdfGeneratorConfig(
                mustacheResourcePath = "templates",
                useTestPdfNotice = true,
            )
            val pdfGenerator = PdfGenerator(cfg)

            val result = pdfGenerator.generate(
                documentMeta = cosMeta,
            )
            result.shouldBeRight()

            val text = extractText(result.value)
            text shouldContain "TESTDOKUMENT"
            text shouldContain "Balance Supplier"
            Loader.loadPDF(result.value).use { document ->
                val metadata = document.documentInformation
                val testDocument = metadata.getCustomMetadataValue("testDocument")

                testDocument shouldBe "true"
            }
        }

        test("Generates PDF without watermark and metadata for change of supplier meta") {
            val cfg = PdfGeneratorConfig(
                mustacheResourcePath = "templates",
                useTestPdfNotice = false,
            )
            val pdfGenerator = PdfGenerator(cfg)

            val result = pdfGenerator.generate(
                documentMeta = cosMeta,
            )
            result.shouldBeRight()

            val text = extractText(result.value)
            text shouldNotContain "TESTDOKUMENT"
            text shouldContain "Balance Supplier"
            Loader.loadPDF(result.value).use { document ->
                val metadata = document.documentInformation
                val testDocument = metadata.getCustomMetadataValue("testDocument")

                testDocument.shouldBeNull()
            }
        }

        test("Generates PDF with watermark and metadata for move in and change of supplier meta") {
            val cfg = PdfGeneratorConfig(
                mustacheResourcePath = "templates",
                useTestPdfNotice = true,
            )
            val pdfGenerator = PdfGenerator(cfg)

            val result = pdfGenerator.generate(
                documentMeta = moveInMeta,
            )

            result.shouldBeRight()
            val text = extractText(result.value)
            text shouldContain "TESTDOKUMENT"
            text shouldContain "Greatest Balance Supplier of all"
            Loader.loadPDF(result.value).use { document ->
                val metadata = document.documentInformation
                val testDocument = metadata.getCustomMetadataValue("testDocument")

                testDocument shouldBe "true"
            }
        }

        test("Generates PDF without watermark and metadata for move in and change of supplier meta") {
            val cfg = PdfGeneratorConfig(
                mustacheResourcePath = "templates",
                useTestPdfNotice = false,
            )
            val pdfGenerator = PdfGenerator(cfg)

            val result = pdfGenerator.generate(
                documentMeta = moveInMeta,
            )

            result.shouldBeRight()
            val text = extractText(result.value)
            text shouldNotContain "TESTDOKUMENT"
            text shouldContain "Greatest Balance Supplier of all"
            Loader.loadPDF(result.value).use { document ->
                val metadata = document.documentInformation
                val testDocument = metadata.getCustomMetadataValue("testDocument")

                testDocument.shouldBeNull()
            }
        }
    })

fun extractText(pdf: ByteArray): String =
    Loader.loadPDF(pdf).use { document ->
        PDFTextStripper().getText(document)
    }

fun extractLanguage(pdf: ByteArray): String? =
    Loader.loadPDF(pdf).use { document ->
        document.documentCatalog.language
    }

fun extractMetadata(pdf: ByteArray): PDDocumentInformation =
    Loader.loadPDF(pdf).use { document ->
        document.documentInformation
    }
