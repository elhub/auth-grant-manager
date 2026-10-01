package no.elhub.auth.common.documents.pdf

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.TemplateFunction
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.StringWriter
import java.util.Locale
import java.util.ResourceBundle

class OrganizationAuthorizationTemplateTest : FunSpec({
    test("renders a change-of-supplier confirmation for an organization") {
        val html =
            renderOrganizationTemplate(
                templatePath = "businessprocesses/changeofbalancesupplier/change_of_supplier.mustache",
                processBundleName = "templates.businessprocesses.changeofbalancesupplier.i18n.messages",
                language = "nb",
                data = organizationData,
            )

        html shouldContain "Avtalebekreftelse - Leverandørskifte"
        html shouldContain "Kunde:</span> Navn AS"
        html shouldContain "Organisasjonsnummer:</span> 100 010 001"
        html.substringAfter("Kunde:</span> Navn AS").substringBefore("Adresse:</span>") shouldContain
            "Organisasjonsnummer:</span> 100 010 001"
        html shouldContain "Adresse:</span> Bjørkeveien 18C, 0168 Oslo"
        html shouldContain "Avtalereferanse:</span> ABC123"
        html shouldContain "strømavtalen som det vises til over"
        html shouldContain "Ved å signere dette dokumentet bekrefter du på vegne av organisasjonen"
        html shouldContain "For å få gjennomført leverandørskifte må strømavtalen bekreftes innen 4 uker."
    }

    test("renders the supplied move-in confirmation text for an organization") {
        val html =
            renderOrganizationTemplate(
                templatePath = "businessprocesses/moveinandchangeofbalancesupplier/move_in.mustache",
                processBundleName = "templates.businessprocesses.moveinandchangeofbalancesupplier.i18n.messages",
                language = "nb",
                data = organizationData + ("moveInDate" to "1. mai 2026"),
            )

        html shouldContain "Avtalebekreftelse - Innflytting og leverandørskifte"
        html shouldContain "Kunde:</span> Navn AS"
        html shouldContain "Organisasjonsnummer:</span> 100 010 001"
        html.substringAfter("Kunde:</span> Navn AS").substringBefore("Adresse:</span>") shouldContain
            "Organisasjonsnummer:</span> 100 010 001"
        html shouldContain "Adresse:</span> Bjørkeveien 18C, 0168 Oslo"
        html shouldContain "Målernummer:</span> 57390234"
        html shouldContain "MålepunktID:</span> 707057500047917289"
        html shouldContain "Strømleverandør:</span> Norgesstrøm"
        html shouldContain "Avtalereferanse:</span> ABC123"
        html shouldContain "strømavtalen som det vises til over"
        html shouldContain "Innflyttingsdato:</span> 1. mai 2026"
        html shouldContain (
            "Reguleringsmyndigheten for Energi (RME) har pålagt Elhub å kontrollere at det foreligger " +
                "gyldig strømavtale før innflytting og skifte av strømleverandør (leverandørskifte)."
            )
        html shouldContain "Ved å signere dette dokumentet bekrefter du på vegne av organisasjonen"
        html shouldContain "må forespørselen bekreftes innen 4 uker."
        html shouldContain "Om strømavtalen ikke bekreftes gjennomføres ikke innflytting og leverandørskiftet."
        html shouldContain "Dette dokumentet vil være tilgjengelig for strømkunden på Elhub Min Side."
        html shouldContain "finner du på Elhub sin hjemmeside."
    }

    test("renders organization confirmations in English") {
        val changeOfSupplier =
            renderOrganizationTemplate(
                templatePath = "businessprocesses/changeofbalancesupplier/change_of_supplier.mustache",
                processBundleName = "templates.businessprocesses.changeofbalancesupplier.i18n.messages",
                language = "en",
                data = organizationData,
            )
        val moveIn =
            renderOrganizationTemplate(
                templatePath = "businessprocesses/moveinandchangeofbalancesupplier/move_in.mustache",
                processBundleName = "templates.businessprocesses.moveinandchangeofbalancesupplier.i18n.messages",
                language = "en",
                data = organizationData + ("moveInDate" to "May 1, 2026"),
            )

        changeOfSupplier shouldContain "on behalf of the organization"
        changeOfSupplier shouldContain "Agreement reference:</span> ABC123"
        moveIn shouldContain "Confirmation of electricity supply agreement - Move-in and change of supplier"
        moveIn shouldContain "Agreement reference:</span> ABC123"
        moveIn shouldContain "request must be confirmed within four weeks."
        moveIn shouldContain "electricity customer on Elhub My Page."
    }

    test("renders organization confirmations in Nynorsk") {
        val changeOfSupplier =
            renderOrganizationTemplate(
                templatePath = "businessprocesses/changeofbalancesupplier/change_of_supplier.mustache",
                processBundleName = "templates.businessprocesses.changeofbalancesupplier.i18n.messages",
                language = "nn",
                data = organizationData,
            )
        val moveIn =
            renderOrganizationTemplate(
                templatePath = "businessprocesses/moveinandchangeofbalancesupplier/move_in.mustache",
                processBundleName = "templates.businessprocesses.moveinandchangeofbalancesupplier.i18n.messages",
                language = "nn",
                data = organizationData + ("moveInDate" to "1. mai 2026"),
            )

        changeOfSupplier shouldContain "på vegner av organisasjonen"
        changeOfSupplier shouldContain "Avtalereferanse:</span> ABC123"
        moveIn shouldContain "Stadfesting av straumavtale - Innflytting og leverandørskifte"
        moveIn shouldContain "Avtalereferanse:</span> ABC123"
        moveIn shouldContain "førespurnaden stadfestast innan 4 veker."
        moveIn shouldContain "straumkunden på Elhub Mi Side."
    }

    test("omits the move-in date when it is absent") {
        val html =
            renderOrganizationTemplate(
                templatePath = "businessprocesses/moveinandchangeofbalancesupplier/move_in.mustache",
                processBundleName = "templates.businessprocesses.moveinandchangeofbalancesupplier.i18n.messages",
                language = "nb",
                data = organizationData,
            )

        html shouldNotContain "Innflyttingsdato:"
    }

    test("omits the contract reference when it is absent") {
        val dataWithoutAgreementReference = organizationData - "agreementReference"
        val changeOfSupplier =
            renderOrganizationTemplate(
                templatePath = "businessprocesses/changeofbalancesupplier/change_of_supplier.mustache",
                processBundleName = "templates.businessprocesses.changeofbalancesupplier.i18n.messages",
                language = "nb",
                data = dataWithoutAgreementReference,
            )
        val moveIn =
            renderOrganizationTemplate(
                templatePath = "businessprocesses/moveinandchangeofbalancesupplier/move_in.mustache",
                processBundleName = "templates.businessprocesses.moveinandchangeofbalancesupplier.i18n.messages",
                language = "nb",
                data = dataWithoutAgreementReference,
            )

        changeOfSupplier shouldNotContain "Strømavtale:"
        moveIn shouldNotContain "Strømavtale:"
        changeOfSupplier shouldContain "har inngått en strømavtale"
        moveIn shouldContain "har inngått en strømavtale"
        changeOfSupplier shouldNotContain "som det vises til over"
        moveIn shouldNotContain "som det vises til over"

        val english =
            renderOrganizationTemplate(
                templatePath = "businessprocesses/changeofbalancesupplier/change_of_supplier.mustache",
                processBundleName = "templates.businessprocesses.changeofbalancesupplier.i18n.messages",
                language = "en",
                data = dataWithoutAgreementReference,
            )
        val nynorsk =
            renderOrganizationTemplate(
                templatePath = "businessprocesses/moveinandchangeofbalancesupplier/move_in.mustache",
                processBundleName = "templates.businessprocesses.moveinandchangeofbalancesupplier.i18n.messages",
                language = "nn",
                data = dataWithoutAgreementReference,
            )

        english shouldContain "has entered into an electricity supply agreement"
        nynorsk shouldContain "har inngått ein straumavtale"
    }
})

private val organizationData =
    mapOf(
        "customerName" to "Navn AS",
        "organizationNumber" to "100 010 001",
        "meteringPointAddress" to "Bjørkeveien 18C, 0168 Oslo",
        "meterNumber" to "57390234",
        "meteringPointId" to "707057500047917289",
        "balanceSupplierName" to "Norgesstrøm",
        "agreementReference" to "ABC123",
        "htmlLang" to "nb",
    )

private fun renderOrganizationTemplate(
    templatePath: String,
    processBundleName: String,
    language: String,
    data: Map<String, Any?>,
): String {
    val locale = Locale.forLanguageTag(language)
    val processBundle = ResourceBundle.getBundle(processBundleName, locale)
    val commonBundle = ResourceBundle.getBundle("templates.i18n.common.messages", locale)
    val templateData =
        data +
            ("htmlLang" to language) +
            (
                "i18n" to
                    TemplateFunction { key ->
                        val normalizedKey = key.trim()
                        when {
                            processBundle.containsKey(normalizedKey) -> processBundle.getString(normalizedKey)
                            commonBundle.containsKey(normalizedKey) -> commonBundle.getString(normalizedKey)
                            else -> normalizedKey
                        }
                    }
                )

    return StringWriter().also { writer ->
        DefaultMustacheFactory("templates")
            .compile(templatePath)
            .execute(writer, templateData)
            .flush()
    }.toString()
}
