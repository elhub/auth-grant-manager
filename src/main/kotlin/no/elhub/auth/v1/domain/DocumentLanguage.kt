package no.elhub.auth.v1.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DocumentLanguage {
    @SerialName("nb")
    NB,

    @SerialName("nn")
    NN,

    @SerialName("en")
    EN,
}
