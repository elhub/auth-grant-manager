package no.elhub.auth.grantmanager.presentation

import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import no.elhub.auth.grantmanager.presentation.config.configureDatabase
import no.elhub.auth.grantmanager.presentation.config.configureKoin
import no.elhub.auth.grantmanager.presentation.config.configureLogging
import no.elhub.auth.grantmanager.presentation.config.configureMonitoring
import no.elhub.auth.grantmanager.presentation.config.configureRouting
import no.elhub.auth.grantmanager.presentation.config.configureSecurity
import no.elhub.auth.grantmanager.presentation.config.configureSerialization

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
