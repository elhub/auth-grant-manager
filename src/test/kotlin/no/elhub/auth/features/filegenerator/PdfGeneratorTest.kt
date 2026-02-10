package no.elhub.auth.features.filegenerator

import arrow.core.getOrElse
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.datetime.LocalDate
import no.elhub.auth.features.businessprocesses.changeofsupplier.domain.ChangeOfSupplierBusinessMeta
import no.elhub.auth.features.businessprocesses.movein.domain.MoveInBusinessMeta
import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.common.currentTimeWithTimeZone
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.ExposedPartyRepository
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.filegenerator.PdfGenerator
import no.elhub.auth.features.filegenerator.PdfGeneratorConfig
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.grants.common.AuthorizationScopeTable
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

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
        test("Generates PDF without watermark and correct metadata for change of supplier metadata") {
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
