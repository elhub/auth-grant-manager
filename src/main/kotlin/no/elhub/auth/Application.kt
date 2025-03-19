package no.elhub.auth

import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import no.elhub.auth.config.configureDatabase
import no.elhub.auth.config.configureKoin
import no.elhub.auth.config.configureLogging
import no.elhub.auth.config.configureMonitoring
import no.elhub.auth.config.configureRouting
import no.elhub.auth.config.configureSecurity
import no.elhub.auth.config.configureSerialization

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
