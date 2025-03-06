package no.elhub.devxp

import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import no.elhub.devxp.config.configureDatabase
import no.elhub.devxp.config.configureKoin
import no.elhub.devxp.config.configureLogging
import no.elhub.devxp.config.configureMonitoring
import no.elhub.devxp.config.configureRouting
import no.elhub.devxp.config.configureSecurity
import no.elhub.devxp.config.configureSerialization

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureDatabase()
    configureKoin()
    configureLogging()
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureRouting()
}
