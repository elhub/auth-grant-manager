package no.elhub.auth.bootstrap

import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import no.elhub.auth.presentation.plugin.configureLogging
import no.elhub.auth.presentation.plugin.configureMonitoring
import no.elhub.auth.presentation.route.configureRouting
import no.elhub.auth.presentation.plugin.configureSecurity
import no.elhub.auth.presentation.plugin.configureSerialization

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
