package no.elhub.auth.features.featuretoggles

import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.server.config.MapApplicationConfig
import io.mockk.mockk

class UnleashFactoryTest : FunSpec({
    test("defaults the Unleash integration to disabled") {
        val settings = MapApplicationConfig().toUnleashSettings()

        settings.enabled.shouldBeFalse()
    }

    test("creates FakeUnleash when the integration is disabled") {
        val settings = MapApplicationConfig(
            "enabled" to "false",
        ).toUnleashSettings()

        createUnleash(settings).shouldBeInstanceOf<FakeUnleash>()
    }

    test("configures DefaultUnleash when the integration is enabled") {
        val unleash = mockk<Unleash>()
        lateinit var capturedConfig: UnleashConfig
        val settings = MapApplicationConfig(
            "enabled" to "true",
            "url" to "https://unleash.example/api",
            "apiToken" to "token",
            "appName" to "auth-grant-manager",
        ).toUnleashSettings()

        val result = createUnleash(settings) { config ->
            capturedConfig = config
            unleash
        }

        result shouldBe unleash
        capturedConfig.appName shouldBe "auth-grant-manager"
        capturedConfig.unleashAPI.toString() shouldBe "https://unleash.example/api"
        capturedConfig.apiKey shouldBe "token"
        capturedConfig.isSynchronousFetchOnInitialisation.shouldBeFalse()
        capturedConfig.isProxyAuthenticationByJvmProperties.shouldBeTrue()
    }
})
