package no.elhub.auth.features.common.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.elhub.auth.features.common.auth.PDPAuthorizationProvider.Companion.Headers

@Serializable
data class PdpRequest(
    val input: Input
)

@Serializable
data class Input(
    @SerialName("ElhubTraceId")
    val elhubTraceId: String,
    val token: String,
    val payload: PdpPayload? = null
)

@Serializable
sealed interface PdpPayload {
    @Serializable
    data class Self(
        @SerialName(Headers.SENDER_GLN)
        val senderGLN: String
    ) : PdpPayload

    @Serializable
    data class Delegated(
        @SerialName(Headers.SENDER_GLN)
        val senderGLN: String,
        @SerialName(Headers.ON_BEHALF_OF_GLN)
        val onBehalfOfGLN: String
    ) : PdpPayload
}
