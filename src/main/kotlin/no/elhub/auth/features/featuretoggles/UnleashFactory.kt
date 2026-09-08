package no.elhub.auth.features.featuretoggles

import io.getunleash.DefaultUnleash
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import io.ktor.server.config.ApplicationConfig

internal data class UnleashSettings(
    val enabled: Boolean,
    val url: String?,
    val apiToken: String?,
    val appName: String?,
)

internal fun ApplicationConfig.toUnleashSettings() =
    UnleashSettings(
        enabled = propertyOrNull("enabled")?.getString()?.toBooleanStrict() ?: false,
        url = propertyOrNull("url")?.getString(),
        apiToken = propertyOrNull("apiToken")?.getString(),
        appName = propertyOrNull("appName")?.getString(),
    )

internal fun createUnleash(
    settings: UnleashSettings,
    defaultUnleashFactory: (UnleashConfig) -> Unleash = ::DefaultUnleash,
): Unleash {
    if (!settings.enabled) {
        return FakeUnleash()
    }

    val config = UnleashConfig.builder()
        .appName(settings.appName.required("appName"))
        .unleashAPI(settings.url.required("url"))
        .apiKey(settings.apiToken.required("apiToken"))
        .synchronousFetchOnInitialisation(false)
        .enableProxyAuthenticationByJvmProperties()
        .build()

    return defaultUnleashFactory(config)
}

private fun String?.required(name: String): String =
    requireNotNull(this?.takeIf(String::isNotBlank)) {
        "Unleash configuration '$name' must be set when Unleash is enabled"
    }
