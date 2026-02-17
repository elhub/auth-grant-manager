package no.elhub.auth.features.documents.create.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.elhub.auth.features.filegenerator.SupportedLanguage

@Serializable
enum class SupportedLanguageDTO {
    @SerialName("nb")
    NB,

    @SerialName("nn")
    NN,

    @SerialName("en")
    EN;

    companion object {
        val DEFAULT = NB
    }
}

fun SupportedLanguageDTO.toSupportedLanguage(): SupportedLanguage =
    when (this) {
        SupportedLanguageDTO.NB -> SupportedLanguage.NB
        SupportedLanguageDTO.NN -> SupportedLanguage.NN
        SupportedLanguageDTO.EN -> SupportedLanguage.EN
    }
