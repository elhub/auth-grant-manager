package no.elhub.auth.common.documents.pdf

import kotlinx.datetime.LocalDate
import java.nio.file.Files
import java.nio.file.Path

/** Sample documents for visually inspecting the production PDF rendering path. Add a sample for each new content type. */
private val samples: List<AuthorizationDocumentPdfContent> = listOf(
    AuthorizationDocumentPdfContent.ChangeOfBalanceSupplier(
        language = PdfLanguage.EN,
        customerName = "Kari Nordmann",
        meteringPointAddress = "Storgata 1, 0155 Oslo",
        meteringPointId = "707057500000000001",
        meterNumber = "123456789",
        balanceSupplierName = "Eksempel Strøm AS",
        balanceSupplierContractName = "Fastpris 12 måneder",
    ),
    AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplier(
        language = PdfLanguage.NB,
        customerName = "Kari Nordmann",
        meteringPointAddress = "Storgata 1, 0155 Oslo",
        meteringPointId = "707057500000000001",
        meterNumber = "123456789",
        balanceSupplierName = "Eksempel Strøm AS",
        balanceSupplierContractName = "Fastpris 12 måneder",
        moveInDate = LocalDate(2026, 10, 1),
    ),
    AuthorizationDocumentPdfContent.FrameworkAgreement(
        language = PdfLanguage.NB,
        organizationName = "Eksempel Organisasjon AS",
        organizationNumber = "100 010 001",
        balanceSupplierName = "Eksempel Strøm AS",
        contractReference = "Rammeavtale 12 måneder",
        startDate = LocalDate(2027, 1, 1),
        endDate = LocalDate(2029, 12, 31),
    ),
)

private fun AuthorizationDocumentPdfContent.previewName(): String = when (this) {
    is AuthorizationDocumentPdfContent.ChangeOfBalanceSupplier -> "change-of-balance-supplier"
    is AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplier -> "move-in-and-change-of-balance-supplier"
    is AuthorizationDocumentPdfContent.FrameworkAgreement -> "framework-agreement"
}

private fun AuthorizationDocumentPdfContent.withLanguage(language: PdfLanguage): AuthorizationDocumentPdfContent =
    when (this) {
        is AuthorizationDocumentPdfContent.ChangeOfBalanceSupplier -> copy(language = language)
        is AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplier -> copy(language = language)
        is AuthorizationDocumentPdfContent.FrameworkAgreement -> copy(language = language)
    }

fun main(args: Array<String>) {
    require(args.size in 2..3) { "Expected document name (or 'all'), output directory, and optional language (nb, nn, en)" }
    val (document, outputDirectory) = args
    val language =
        args.getOrNull(2)
            ?.takeIf { it.isNotBlank() }
            ?.let { code ->
                PdfLanguage.values().firstOrNull { it.code.equals(code, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Unsupported language '$code'. Available: nb, nn, en")
            } ?: PdfLanguage.NB
    val namedSamples = samples.associateBy { it.previewName() }
    val selectedSamples = if (document == "all") {
        namedSamples
    } else {
        mapOf(
            document to requireNotNull(namedSamples[document]) {
                "Unknown document '$document'. Available: all, ${namedSamples.keys.joinToString()}"
            }
        )
    }
    val selected =
        selectedSamples.mapValues { (_, content) ->
            content.withLanguage(language)
        }

    val output = Path.of(outputDirectory)
    Files.createDirectories(output)
    val generator = MustachePdfGenerator(PdfGeneratorConfig("templates", useTestPdfNotice = false))
    selected.forEach { (name, content) ->
        val file = output.resolve("$name.pdf")
        Files.write(file, generator.generate(content))
        println("Generated $file")
    }
}
