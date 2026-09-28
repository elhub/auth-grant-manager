package no.elhub.auth.common

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.di.DI
import no.elhub.auth.v0.config.HeaderPolicy
import no.elhub.auth.v0.config.configModule
import no.elhub.auth.v0.config.configureAuthorization
import no.elhub.auth.v0.config.configureDatabase
import no.elhub.auth.v0.config.configureLogging
import no.elhub.auth.v0.config.configureMonitoring
import no.elhub.auth.v0.config.configureRequestTracing
import no.elhub.auth.v0.config.configureSerialization

fun Application.module() {
    install(DI)

    val dataSource = configureDatabase()
    configureRequestTracing()
    configureLogging()
    configureMonitoring(dataSource)
    configureSerialization()
    install(HeaderPolicy)
    configureErrorHandling()
    configModule()
    configureAuthorization()
}
