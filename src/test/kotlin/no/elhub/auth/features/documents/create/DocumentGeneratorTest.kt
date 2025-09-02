package no.elhub.auth.features.documents.create

import eu.europa.esig.dss.model.InMemoryDocument
import eu.europa.esig.dss.pades.validation.PDFDocumentValidator
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import io.kotest.assertions.print.print
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import no.elhub.auth.features.documents.PdfGenerator
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.ValidationResult


class DocumentGeneratorTest : FunSpec({

    test("Placeholder test") {
        val b = PdfGenerator.createChangeOfSupplierConfirmationPdf("ssn", "supplier", "meteringPointId")
        // Generate unique filename with random UUID
        val outPath = Paths.get("build/test-output/change-of-supplier-${UUID.randomUUID()}.pdf")
        Files.createDirectories(outPath.parent)
        Files.write(outPath, b)
        print(validatePdfA1b(b).failedChecks)
        validatePdfA1b(b).isCompliant shouldBe true

    }

})


@Throws(Exception::class)
fun validatePdfA1b(bytes: ByteArray): ValidationResult {
    VeraGreenfieldFoundryProvider.initialise() // once
    val flavour = PDFAFlavour.PDFA_2_B
    Foundries.defaultInstance().createParser(ByteArrayInputStream(bytes), flavour).use { parser ->
        val validator = Foundries.defaultInstance().createValidator(flavour, false)
        return validator.validate(parser)
    }
}
