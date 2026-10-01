package no.elhub.auth.common.documents.pdf

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.TemplateFunction
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.StringWriter
import java.util.Locale
import java.util.ResourceBundle

class FrameworkAgreementTemplateTest : FunSpec({
    test("renders organization framework agreement content without signer identity") {
        val html = renderFrameworkAgreement(
            mapOf(
                "organizationName" to "Navn AS",
                "organizationNumber" to "100 010 001",
                "balanceSupplierName" to "Elvekraft",
                "contractReference" to "Elvekraft Rammeavtale ABC213",
                "startDate" to "01. Januar 2027",
                "endDate" to "",
            ),
        )

        html shouldContain "Avtalebekreftelse - Rammeavtale"
        html shouldContain "Navn AS"
        html shouldContain "100 010 001"
        html.substringAfter("Kunde:</span> Navn AS").substringBefore("Strømleverandør:") shouldContain
            "Organisasjonsnummer:</span> 100 010 001"
        html shouldContain "Strømavtale:</span> Elvekraft Rammeavtale ABC213"
        html shouldContain "Strømleverandør"
        html shouldContain "01. Januar 2027"
        html shouldContain "Løpende avtale"
        html shouldContain "Reguleringsmyndigheten for energi (RME) har pålagt Elhub å kontrollere"
        html shouldContain "Ved å signere dette dokumentet bekrefter du på vegne av organisasjonen"
        html shouldContain "organisasjonen har inngått rammeavtalen som det vises til over"
        html shouldContain "Bekreftelsen er ikke knyttet til bestemte målepunkter"
        html shouldNotContain "Jon Janson"
        html shouldNotContain "20.10.1990"
    }

    test("renders English text from the locale bundle") {
        val html = renderFrameworkAgreement(exampleData, "en")

        html shouldContain "Framework agreement confirmation"
        html shouldContain "Organization number"
        html shouldContain "Ongoing agreement"
        html shouldContain "entered into the framework agreement referenced above"
    }

    test("renders Nynorsk text from the locale bundle") {
        val html = renderFrameworkAgreement(exampleData, "nn")

        html shouldContain "Avtalestadfesting - Rammeavtale"
        html shouldContain "Organisasjonsnummer"
        html shouldContain "Løpande avtale"
        html shouldContain "organisasjonen har inngått rammeavtalen som det blir vist til over"
    }
})

private val exampleData =
    mapOf(
        "organizationName" to "Navn AS",
        "organizationNumber" to "100 010 001",
        "balanceSupplierName" to "Elvekraft",
        "contractReference" to "Elvekraft Rammeavtale ABC213",
        "startDate" to "01. Januar 2027",
        "endDate" to "",
    )

private fun renderFrameworkAgreement(data: Map<String, Any?>, language: String = "nb"): String {
    val locale = Locale.forLanguageTag(language)
    val processBundle =
        ResourceBundle.getBundle(
            "templates.businessprocesses.frameworkagreement.i18n.messages",
            locale,
        )
    val commonBundle = ResourceBundle.getBundle("templates.i18n.common.messages", locale)
    val templateData =
        data +
            mapOf(
                "i18n" to TemplateFunction { key ->
                    val normalizedKey = key.trim()
                    when {
                        processBundle.containsKey(normalizedKey) -> processBundle.getString(normalizedKey)
                        commonBundle.containsKey(normalizedKey) -> commonBundle.getString(normalizedKey)
                        else -> normalizedKey
                    }
                },
                "htmlLang" to language,
            )

    return StringWriter().also { writer ->
        DefaultMustacheFactory("templates")
            .compile("businessprocesses/frameworkagreement/framework_agreement.mustache")
            .execute(writer, templateData)
            .flush()
    }.toString()
}
