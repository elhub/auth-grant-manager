package no.elhub.auth

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import no.elhub.auth.config.baseModule
import no.elhub.auth.config.configureDatabase
import no.elhub.auth.config.configureLogging
import no.elhub.auth.config.configureMonitoring
import no.elhub.auth.config.configureSerialization
import no.elhub.auth.features.requests.configureRequestsRouting
import no.elhub.auth.features.requests.requestsModule
import org.koin.ktor.plugin.Koin

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    install(Koin) {
        modules(
            baseModule,
            requestsModule
        )
    }

    val dataSource = configureDatabase()
    configureLogging()
    configureMonitoring(dataSource)
    configureSerialization()
    configureRequestsRouting()
}
