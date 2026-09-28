package no.elhub.auth.v0.features.filegenerator

import no.elhub.auth.common.documents.pdf.PdfLanguage

enum class SupportedLanguage(
    val code: String
) {
    NB("nb"),
    NN("nn"),
    EN("en");

    fun toPdfLanguage() = when (this) {
        NB -> "nb-NO"
        NN -> "nn-NO"
        EN -> "en-US"
    }

    companion object {
        val DEFAULT = NB
    }

    fun toCommonPdfLanguage() = when (this) {
        NB -> PdfLanguage.NB
        NN -> PdfLanguage.NN
        EN -> PdfLanguage.EN
    }
}
