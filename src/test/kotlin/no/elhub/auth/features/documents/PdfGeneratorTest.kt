package no.elhub.auth.features.documents

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.PDFAParser
import org.verapdf.pdfa.PDFAValidator
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.TestAssertion
import org.verapdf.pdfa.results.ValidationResult

class PdfGeneratorTest : FunSpec({

    VeraGreenfieldFoundryProvider.initialise();



    test("should generate PDF/2A-b compliant PDF document") {
        val pdfBytes = PdfGenerator.createChangeOfSupplierConfirmationPdf(
            meteringPointId = "123456789012345678",
            ssn = "12345678901",
            supplier = "Test Supplier"
        )

        val flavour = PDFAFlavour.PDFA_2_B
        val validationResult = validatePdf(pdfBytes, flavour)
        validationResult.isCompliant shouldBe true




}

})


private fun validatePdf(pdfBytes: ByteArray, flavour: PDFAFlavour): ValidationResult {
    ByteArrayInputStream(pdfBytes).use { input ->
        Foundries.defaultInstance().createParser(input, flavour).use { parser: PDFAParser ->
            val validator: PDFAValidator =
                Foundries.defaultInstance().createValidator(flavour, false)
            return validator.validate(parser)
        }
    }
}
