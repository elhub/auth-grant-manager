package no.elhub.auth.common

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.di.DI
import no.elhub.auth.common.config.HeaderPolicy
import no.elhub.auth.common.config.ResolveAuthorizationHeaders
import no.elhub.auth.common.config.configModule
import no.elhub.auth.common.config.configureAuthorization
import no.elhub.auth.common.config.configureDatabase
import no.elhub.auth.common.config.configureLogging
import no.elhub.auth.common.config.configureMonitoring
import no.elhub.auth.common.config.configureRequestTracing
import no.elhub.auth.common.config.configureSerialization

fun Application.module() {
    install(DI)

    val dataSource = configureDatabase()
    configureRequestTracing()
    configureLogging()
    configureMonitoring(dataSource)
    configureSerialization()
    install(HeaderPolicy)
    install(ResolveAuthorizationHeaders)
    configureErrorHandling()
    configModule()
    configureAuthorization()
}
