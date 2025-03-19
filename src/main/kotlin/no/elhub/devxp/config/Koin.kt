package no.elhub.devxp.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.ksp.generated.defaultModule
import org.koin.ktor.plugin.Koin

fun Application.configureKoin() {
    install(Koin) {
        defaultModule()
    }
}
