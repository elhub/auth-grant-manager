package no.elhub.auth.v0.features.documents.create

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.common.documents.pdf.PdfGenerationException
import no.elhub.auth.common.documents.pdf.PdfGenerator
import no.elhub.auth.v0.features.businessprocesses.changeofbalancesupplier.domain.ChangeOfBalanceSupplierBusinessMeta
import no.elhub.auth.v0.features.businessprocesses.moveinandchangeofbalancesupplier.domain.MoveInAndChangeOfBalanceSupplierBusinessMeta
import no.elhub.auth.v0.features.filegenerator.SupportedLanguage

class V0FileGeneratorAdapterTest : FunSpec({
    test("maps change-of-supplier metadata to common PDF content") {
        val generator = CapturingPdfGenerator()
        val meta = ChangeOfBalanceSupplierBusinessMeta(
            requestedFromName = "Customer",
            requestedForMeteringPointId = "metering-point",
            requestedForMeterNumber = "meter",
            requestedForMeteringPointAddress = "Address",
            balanceSupplierName = "Supplier",
            balanceSupplierContractName = "Contract",
            language = SupportedLanguage.EN,
        )

        V0FileGeneratorAdapter(generator).generate(meta).shouldBeRight() shouldBe byteArrayOf(1)
        generator.captured shouldBe AuthorizationDocumentPdfContent.ChangeOfBalanceSupplier(
            language = no.elhub.auth.common.documents.pdf.PdfLanguage.EN,
            customerName = "Customer",
            meteringPointAddress = "Address",
            meteringPointId = "metering-point",
            meterNumber = "meter",
            balanceSupplierName = "Supplier",
            balanceSupplierContractName = "Contract",
        )
    }

    test("maps move-in metadata and preserves the move-in date") {
        val generator = CapturingPdfGenerator()
        val moveInDate = LocalDate(2026, 2, 3)
        val meta = MoveInAndChangeOfBalanceSupplierBusinessMeta(
            requestedFromName = "Customer",
            requestedForMeteringPointId = "metering-point",
            requestedForMeterNumber = "meter",
            requestedForMeteringPointAddress = "Address",
            balanceSupplierName = "Supplier",
            balanceSupplierContractName = "Contract",
            moveInDate = moveInDate,
            language = SupportedLanguage.NN,
        )

        V0FileGeneratorAdapter(generator).generate(meta).shouldBeRight() shouldBe byteArrayOf(1)
        generator.captured shouldBe AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplier(
            language = no.elhub.auth.common.documents.pdf.PdfLanguage.NN,
            customerName = "Customer",
            meteringPointAddress = "Address",
            meteringPointId = "metering-point",
            meterNumber = "meter",
            balanceSupplierName = "Supplier",
            balanceSupplierContractName = "Contract",
            moveInDate = moveInDate,
        )
    }

    test("uses the default language when metadata has no language") {
        val generator = CapturingPdfGenerator()
        val meta = ChangeOfBalanceSupplierBusinessMeta(
            requestedFromName = "Customer",
            requestedForMeteringPointId = "metering-point",
            requestedForMeterNumber = "meter",
            requestedForMeteringPointAddress = "Address",
            balanceSupplierName = "Supplier",
            balanceSupplierContractName = "Contract",
        )

        V0FileGeneratorAdapter(generator).generate(meta).shouldBeRight()
        generator.captured?.language shouldBe no.elhub.auth.common.documents.pdf.PdfLanguage.DEFAULT
    }

    test("maps PDF generation failures to the v0 content generation error") {
        val generator = object : PdfGenerator {
            override fun generate(content: AuthorizationDocumentPdfContent): ByteArray =
                throw PdfGenerationException(IllegalStateException("failed"))
        }
        val meta = ChangeOfBalanceSupplierBusinessMeta(
            requestedFromName = "Customer",
            requestedForMeteringPointId = "metering-point",
            requestedForMeterNumber = "meter",
            requestedForMeteringPointAddress = "Address",
            balanceSupplierName = "Supplier",
            balanceSupplierContractName = "Contract",
        )

        V0FileGeneratorAdapter(generator).generate(meta).shouldBeLeft() shouldBe
            DocumentGenerationError.ContentGenerationError
    }
})

private class CapturingPdfGenerator : PdfGenerator {
    var captured: AuthorizationDocumentPdfContent? = null

    override fun generate(content: AuthorizationDocumentPdfContent): ByteArray {
        captured = content
        return byteArrayOf(1)
    }
}
