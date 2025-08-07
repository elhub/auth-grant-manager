package no.elhub.auth.presentation

import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import no.elhub.auth.presentation.config.configureDatabase
import no.elhub.auth.presentation.config.configureKoin
import no.elhub.auth.presentation.config.configureLogging
import no.elhub.auth.presentation.config.configureMonitoring
import no.elhub.auth.presentation.config.configureRouting
import no.elhub.auth.presentation.config.configureSecurity
import no.elhub.auth.presentation.config.configureSerialization

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureKoin()
    val dataSource = configureDatabase()
    configureLogging()
    configureMonitoring(dataSource)
    configureSerialization()
    configureSecurity()
    configureRouting()
}
