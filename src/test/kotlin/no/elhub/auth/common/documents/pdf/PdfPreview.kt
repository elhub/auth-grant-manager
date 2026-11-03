package no.elhub.auth.common.documents.pdf

import kotlinx.datetime.LocalDate
import java.nio.file.Files
import java.nio.file.Path

/** Sample documents for visually inspecting the production PDF rendering path. Add a sample for each new content type. */
private val organizationMeteringPoints = listOf(
    MeteringPointPdfContent(
        address = "Bjørkeveien 18C, 0168 Oslo",
        id = "707057500047917289",
        meterNumber = "57390234",
    ),
    MeteringPointPdfContent(
        address = "Storgata 1, 0155 Oslo",
        id = "707057500047917290",
        meterNumber = "57390235",
    ),
    MeteringPointPdfContent(
        address = "Eksempelveien 3, 9348 Sandefjord",
        id = "707057500047917291",
        meterNumber = "57390236",
    ),
    MeteringPointPdfContent(
        address = "Havneveien 12, 3263 Larvik",
        id = "707057500047917292",
        meterNumber = "57390237",
    ),
    MeteringPointPdfContent(
        address = "Skogveien 42, 5005 Bergen",
        id = "707057500047917293",
        meterNumber = "57390238",
    ),
)

private val samples: List<AuthorizationDocumentPdfContent> = listOf(
    AuthorizationDocumentPdfContent.ChangeOfBalanceSupplierForPerson(
        language = PdfLanguage.EN,
        customerName = "Kari Nordmann",
        meteringPoints = listOf(
            MeteringPointPdfContent(
                address = "Storgata 1, 0155 Oslo",
                id = "707057500000000001",
                meterNumber = "123456789",
            ),
        ),
        balanceSupplierName = "Eksempel Strøm AS",
        balanceSupplierContractName = "Fastpris 12 måneder",
    ),
    AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplierForPerson(
        language = PdfLanguage.NB,
        customerName = "Kari Nordmann",
        meteringPoints = listOf(
            MeteringPointPdfContent(
                address = "Storgata 1, 0155 Oslo",
                id = "707057500000000001",
                meterNumber = "123456789",
            ),
        ),
        balanceSupplierName = "Eksempel Strøm AS",
        balanceSupplierContractName = "Fastpris 12 måneder",
        moveInDate = LocalDate(2026, 10, 1),
    ),
    AuthorizationDocumentPdfContent.ChangeOfBalanceSupplierForOrganisation(
        language = PdfLanguage.NB,
        organizationName = "Navn AS",
        organizationNumber = "100 010 001",
        meteringPoints = organizationMeteringPoints,
        balanceSupplierName = "Norgesstrøm",
        agreementReference = "ABC123",
    ),
    AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplierForOrganisation(
        language = PdfLanguage.NB,
        organizationName = "Navn AS",
        organizationNumber = "100 010 001",
        meteringPoints = organizationMeteringPoints,
        balanceSupplierName = "Norgesstrøm",
        agreementReference = "ABC123",
        moveInDate = LocalDate(2026, 5, 1),
    ),
    AuthorizationDocumentPdfContent.EnergySupplierFrameworkAgreement(
        language = PdfLanguage.NB,
        organizationName = "Eksempel Organisasjon AS",
        organizationNumber = "100 010 001",
        balanceSupplierName = "Eksempel Strøm AS",
        agreementReference = "Rammeavtale 12 måneder",
        startDate = LocalDate(2027, 1, 1),
        endDate = LocalDate(2029, 12, 31),
    ),
)

private fun AuthorizationDocumentPdfContent.previewName(): String = when (this) {
    is AuthorizationDocumentPdfContent.ChangeOfBalanceSupplierForPerson -> "change-of-balance-supplier"
    is AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplierForPerson -> "move-in-and-change-of-balance-supplier"
    is AuthorizationDocumentPdfContent.ChangeOfBalanceSupplierForOrganisation -> "change-of-balance-supplier-for-organisation"
    is AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplierForOrganisation -> "move-in-and-change-of-balance-supplier-for-organisation"
    is AuthorizationDocumentPdfContent.EnergySupplierFrameworkAgreement -> "framework-agreement"
}

private fun AuthorizationDocumentPdfContent.withLanguage(language: PdfLanguage): AuthorizationDocumentPdfContent =
    when (this) {
        is AuthorizationDocumentPdfContent.ChangeOfBalanceSupplierForPerson -> copy(language = language)
        is AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplierForPerson -> copy(language = language)
        is AuthorizationDocumentPdfContent.ChangeOfBalanceSupplierForOrganisation -> copy(language = language)
        is AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplierForOrganisation -> copy(language = language)
        is AuthorizationDocumentPdfContent.EnergySupplierFrameworkAgreement -> copy(language = language)
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
