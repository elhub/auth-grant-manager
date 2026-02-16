package no.elhub.auth.features.filegenerator

import arrow.core.Either
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.elhub.auth.features.businessprocesses.changeofenergysupplier.domain.ChangeOfEnergySupplierBusinessMeta

class PdfGeneratorI18nTest : FunSpec({
    test("should generate localized pdf text with default language") {
        val generator = PdfGenerator(
            PdfGeneratorConfig(
                mustacheResourcePath = "templates",
                useTestPdfNotice = false,
            )
        )
        val meta = ChangeOfEnergySupplierBusinessMeta(
            requestedFromName = "Hillary Orr",
            requestedForMeteringPointId = "123456789012345678",
            requestedForMeteringPointAddress = "Example Street 1, 1234 Oslo",
            balanceSupplierName = "Jami Wade",
            balanceSupplierContractName = "Selena Chandler",
        )

        val pdfResult = generator.generate(
            signerNin = "01017012345",
            documentMeta = meta,
            language = SupportedLanguage.DEFAULT,
        )
        val pdfBytes = when (pdfResult) {
            is Either.Left -> error("PDF generation failed for default language")
            is Either.Right -> pdfResult.value
        }
        pdfBytes.isNotEmpty().shouldBeTrue()
        val text = extractText(pdfBytes).normalizeWhitespace()
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
        val meta = ChangeOfEnergySupplierBusinessMeta(
            requestedFromName = "Hillary Orr",
            requestedForMeteringPointId = "123456789012345678",
            requestedForMeteringPointAddress = "Example Street 1, 1234 Oslo",
            balanceSupplierName = "Jami Wade",
            balanceSupplierContractName = "Selena Chandler",
        )

        val englishPdfResult = generator.generate(
            signerNin = "01017012345",
            documentMeta = meta,
            language = SupportedLanguage.EN,
        )
        val englishText = when (englishPdfResult) {
            is Either.Left -> error("PDF generation failed for english language")
            is Either.Right -> extractText(englishPdfResult.value).normalizeWhitespace()
        }

        englishText shouldContain "Agreement confirmation - Change of supplier"
        englishText shouldNotContain "Avtalebekreftelse"
    }
})

private fun String.normalizeWhitespace(): String =
    trim().replace(Regex("\\s+"), " ")
