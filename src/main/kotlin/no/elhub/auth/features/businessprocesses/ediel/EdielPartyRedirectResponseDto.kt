package no.elhub.auth.features.businessprocesses.ediel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EdielPartyRedirectResponseDto(
    @SerialName("RedirectUrls")
    val redirectUrls: EdielRedirectUrlsDto,
)

@Serializable
data class EdielRedirectUrlsDto(
    @SerialName("Production")
    val production: String? = null,
    @SerialName("Test")
    val test: String? = null,
)

fun EdielPartyRedirectResponseDto.redirectUriFor(environment: EdielEnvironment): String? =
    when (environment) {
        EdielEnvironment.PRODUCTION -> redirectUrls.production
        EdielEnvironment.TEST -> redirectUrls.test
    }
