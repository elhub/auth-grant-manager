package no.elhub.auth.utils

import io.ktor.server.testing.TestApplication
import no.elhub.auth.config.configureDatabase
import no.elhub.auth.config.configureKoin
import no.elhub.auth.config.configureLogging
import no.elhub.auth.config.configureRouting
import no.elhub.auth.config.configureSecurity
import no.elhub.auth.config.configureSerialization

fun defaultTestApplication(): TestApplication = TestApplication {
    application {
        configureDatabase()
        configureKoin()
        configureLogging()
        configureSerialization()
        configureSecurity()
        configureRouting()
    }
}
