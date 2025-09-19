package no.elhub.auth

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import no.elhub.auth.config.configureDatabase
import no.elhub.auth.config.configureLogging
import no.elhub.auth.config.configureMonitoring
import no.elhub.auth.config.configureSecurity
import no.elhub.auth.config.configureSerialization
import org.koin.ktor.plugin.Koin

fun main(args: Array<String>) {
    EngineMain.main(args)
}

const val API_PATH = ""

fun Application.module() {
    install(Koin)
    val dataSource = configureDatabase()
    configureLogging()
    configureMonitoring(dataSource)
    configureSerialization()
    configureSecurity()
}
