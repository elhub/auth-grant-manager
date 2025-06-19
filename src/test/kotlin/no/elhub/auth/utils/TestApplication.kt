package no.elhub.auth.utils

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.TestApplication
import no.elhub.auth.module

fun defaultTestApplication(): TestApplication = TestApplication {
    application {
        module()
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
