package no.elhub.auth.features.filegenerator

import arrow.core.Either
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.domain.ChangeOfBalanceSupplierBusinessMeta
import org.apache.pdfbox.Loader

class PdfGeneratorI18nTest : FunSpec({
    test("should generate localized pdf text with default language") {
        val generator = PdfGenerator(
            PdfGeneratorConfig(
                mustacheResourcePath = "templates",
                useTestPdfNotice = false,
            )
        )
        val meta = ChangeOfBalanceSupplierBusinessMeta(
            language = SupportedLanguage.DEFAULT,
            requestedFromName = "Hillary Orr",
            requestedForMeteringPointId = "123456789012345678",
            requestedForMeterNumber = "123456789",
            requestedForMeteringPointAddress = "Example Street 1, 1234 Oslo",
            balanceSupplierName = "Jami Wade",
            balanceSupplierContractName = "Selena Chandler",
        )

        val pdfResult = generator.generate(
            documentMeta = meta,
        )
        val pdfBytes = when (pdfResult) {
            is Either.Left -> error("PDF generation failed for default language")
            is Either.Right -> pdfResult.value
        }
        pdfBytes.isNotEmpty().shouldBeTrue()
        val language = extractLanguage(pdfBytes)
        val text = extractText(pdfBytes).normalizeWhitespace()
        language shouldBe "nb-NO"
        text shouldContain "Hillary Orr"
        text shouldContain "Example Street 1, 1234 Oslo"
        text shouldContain "Jami Wade"
        text shouldContain "Selena Chandler"
    }

    test("should use provided language instead of default language") {
        val generator = PdfGenerator(
            PdfGeneratorConfig(
                mustacheResourcePath = "templates",
                useTestPdfNotice = false,
            )
        )
        val meta = ChangeOfBalanceSupplierBusinessMeta(
            language = SupportedLanguage.EN,
            requestedFromName = "Hillary Orr",
            requestedForMeteringPointId = "123456789012345678",
            requestedForMeterNumber = "123456789",
            requestedForMeteringPointAddress = "Example Street 1, 1234 Oslo",
            balanceSupplierName = "Jami Wade",
            balanceSupplierContractName = "Selena Chandler",
        )

        val englishPdfResult = generator.generate(
            documentMeta = meta,
        )
        val englishPdf = when (englishPdfResult) {
            is Either.Left -> error("PDF generation failed for english language")
            is Either.Right -> englishPdfResult.value
        }

        val languageMetadata = extractLanguage(englishPdf)
        val englishText = extractText(englishPdf).normalizeWhitespace()

        languageMetadata shouldBe "en-US"
        englishText shouldContain "Agreement confirmation - Change of supplier"
        englishText shouldNotContain "Avtalebekreftelse"
    }
})

private fun String.normalizeWhitespace(): String =
    trim().replace(Regex("\\s+"), " ")
