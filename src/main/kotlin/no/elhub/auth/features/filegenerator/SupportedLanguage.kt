package no.elhub.auth.features.filegenerator

enum class SupportedLanguage(
    val code: String
) {
    NB("nb"),
    NN("nn"),
    EN("en");

    companion object {
        val DEFAULT = NB
    }
}
