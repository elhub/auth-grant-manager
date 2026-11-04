package no.elhub.auth.v1.documenthandlers.common

import no.elhub.auth.common.documents.pdf.PdfLanguage
import no.elhub.auth.v1.domain.DocumentLanguage

internal fun DocumentLanguage.toPdfLanguage() = when (this) {
    DocumentLanguage.NB -> PdfLanguage.NB
    DocumentLanguage.NN -> PdfLanguage.NN
    DocumentLanguage.EN -> PdfLanguage.EN
}
