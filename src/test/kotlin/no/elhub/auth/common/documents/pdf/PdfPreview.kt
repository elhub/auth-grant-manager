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
)

private fun AuthorizationDocumentPdfContent.previewName(): String = when (this) {
    is AuthorizationDocumentPdfContent.ChangeOfBalanceSupplier -> "change-of-balance-supplier"
    is AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplier -> "move-in-and-change-of-balance-supplier"
}

fun main(args: Array<String>) {
    require(args.size == 2) { "Expected document name (or 'all') and output directory" }
    val (document, outputDirectory) = args
    val namedSamples = samples.associateBy { it.previewName() }
    val selected = if (document == "all") {
        namedSamples
    } else {
        mapOf(
            document to requireNotNull(namedSamples[document]) {
                "Unknown document '$document'. Available: all, ${namedSamples.keys.joinToString()}"
            }
        )
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
