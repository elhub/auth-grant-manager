package no.elhub.auth.common.documents.pdf

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.apache.pdfbox.Loader
import java.nio.file.Files

class PdfPreviewTest : FunSpec({
    test("defaults the selected PDF to Norwegian Bokmål") {
        val outputDirectory = Files.createTempDirectory("pdf-preview-test")
        val pdfFile = outputDirectory.resolve("change-of-balance-supplier.pdf")

        try {
            main(arrayOf("change-of-balance-supplier", outputDirectory.toString()))

            Loader.loadPDF(pdfFile.toFile()).use { pdf ->
                pdf.documentCatalog.language shouldBe "nb-NO"
            }
        } finally {
            Files.deleteIfExists(pdfFile)
            Files.deleteIfExists(outputDirectory)
        }
    }

    test("generates a selected PDF in the requested language") {
        val outputDirectory = Files.createTempDirectory("pdf-preview-test")
        val pdfFile = outputDirectory.resolve("framework-agreement.pdf")

        try {
            main(arrayOf("framework-agreement", outputDirectory.toString(), "en"))

            Loader.loadPDF(pdfFile.toFile()).use { pdf ->
                pdf.documentCatalog.language shouldBe "en-US"
            }
        } finally {
            Files.deleteIfExists(pdfFile)
            Files.deleteIfExists(outputDirectory)
        }
    }

    test("generates a selected organization change-of-supplier PDF") {
        val outputDirectory = Files.createTempDirectory("pdf-preview-test")
        val pdfFile = outputDirectory.resolve("change-of-balance-supplier-for-organisation.pdf")

        try {
            main(arrayOf("change-of-balance-supplier-for-organisation", outputDirectory.toString()))

            Loader.loadPDF(pdfFile.toFile()).use { pdf ->
                pdf.documentCatalog.language shouldBe "nb-NO"
            }
        } finally {
            Files.deleteIfExists(pdfFile)
            Files.deleteIfExists(outputDirectory)
        }
    }

    test("generates a selected organization move-in PDF") {
        val outputDirectory = Files.createTempDirectory("pdf-preview-test")
        val pdfFile = outputDirectory.resolve("move-in-and-change-of-balance-supplier-for-organisation.pdf")

        try {
            main(arrayOf("move-in-and-change-of-balance-supplier-for-organisation", outputDirectory.toString()))

            Loader.loadPDF(pdfFile.toFile()).use { pdf ->
                pdf.documentCatalog.language shouldBe "nb-NO"
            }
        } finally {
            Files.deleteIfExists(pdfFile)
            Files.deleteIfExists(outputDirectory)
        }
    }
})
