package no.elhub.auth.utils

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.TestApplication
import no.elhub.auth.config.configureDatabase
import no.elhub.auth.config.configureKoin
import no.elhub.auth.config.configureLogging
import no.elhub.auth.config.configureMonitoring
import no.elhub.auth.config.configureRouting
import no.elhub.auth.config.configureSecurity
import no.elhub.auth.config.configureSerialization

fun defaultTestApplication(): TestApplication = TestApplication {
    application {
        configureKoin()
        val dataSource = configureDatabase()
        configureLogging()
        configureMonitoring(dataSource)
        configureSerialization()
        configureSecurity()
        configureRouting()
    }
    environment {
        config = MapApplicationConfig(
            "ktor.database.username" to "app",
            "ktor.database.password" to "app",
            "ktor.database.url" to "jdbc:postgresql://localhost:5432/auth",
            "ktor.database.driverClass" to "org.postgresql.Driver",
        )
    }
}
