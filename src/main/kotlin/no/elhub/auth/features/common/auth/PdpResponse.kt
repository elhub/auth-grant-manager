package no.elhub.auth.features.common.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PdpResponse(
    val result: Result
)

@Serializable
data class Result(
    val tokenInfo: TokenInfo,
    val authInfo: AuthInfo? = null
)

@Serializable
data class TokenInfo(
    val tokenStatus: String,
    val partyId: String? = null,
    val tokenType: String? = null,
    val tokenScope: String? = null
)

@Serializable
enum class ActingType {
    @SerialName("person")
    Person,

    @SerialName("organisation")
    Organisation,
}

@Serializable
data class AuthInfo(
    val inputFailed: String? = null,
    val authorizedFunctions: List<AuthorizedFunction>? = null,
    val actingGLN: String? = null,
    val actingId: String? = null,
    val actingType: ActingType? = null,
    val actingOrganisationNumber: String? = null,
    val originalId: String? = null,
    val error: String? = null,
    val originalOnBehalfOfFunction: String? = null,
    val originalOnBehalfOfGLN: String? = null,
    val originalSenderFunction: String? = null,
    val originalSenderGLN: String? = null
)

@Serializable
data class AuthorizedFunction(
    val functionCode: String? = null,
    val functionName: String? = null
)
