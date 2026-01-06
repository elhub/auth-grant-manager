package no.elhub.auth

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import no.elhub.auth.config.HeaderPolicy
import no.elhub.auth.config.configureDatabase
import no.elhub.auth.config.configureLogging
import no.elhub.auth.config.configureMonitoring
import no.elhub.auth.config.configureRequestTracing
import no.elhub.auth.config.configureSerialization
import org.koin.ktor.plugin.Koin

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    install(Koin)
    val dataSource = configureDatabase()
    configureRequestTracing()
    configureLogging()
    configureMonitoring(dataSource)
    configureSerialization()
    install(HeaderPolicy)
}
