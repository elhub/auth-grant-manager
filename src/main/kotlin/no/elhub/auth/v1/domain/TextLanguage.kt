package no.elhub.auth.v1.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TextLanguage(val code: String) {
    @SerialName("nb")
    NB("nb"),

    @SerialName("nn")
    NN("nn"),

    @SerialName("en")
    EN("en"),
    ;

    companion object {
        fun fromCode(code: String): TextLanguage = entries.single { it.code == code }
    }
}
