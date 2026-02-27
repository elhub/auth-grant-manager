package no.elhub.auth.features.filegenerator

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
}
