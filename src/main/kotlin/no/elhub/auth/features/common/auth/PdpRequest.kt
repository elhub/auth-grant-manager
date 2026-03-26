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
    val payload: PdpPayload
)

@Serializable
data class PdpPayload(
    @SerialName(Headers.SENDER_GLN)
    val senderGLN: String? = null,
    @SerialName(Headers.ON_BEHALF_OF_GLN)
    val onBehalfOfGLN: String? = null,
    @SerialName("OnBehalfOfOrganisationId")
    val onBehalfOfOrganisationId: String? = null,
)
