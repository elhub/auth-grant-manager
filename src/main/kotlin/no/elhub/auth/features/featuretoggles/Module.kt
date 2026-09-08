package no.elhub.auth.features.featuretoggles

import io.getunleash.Unleash
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

fun Application.unleashModule() {
    val settings = environment.config.config("unleash").toUnleashSettings()
    dependencies {
        key<Unleash> {
            provide {
                createUnleash(settings)
            }
            cleanup { unleash ->
                unleash.shutdown()
            }
        }
    }
}
