package no.elhub.auth.config

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

fun Application.configModule() {
    dependencies {
        provide<TransactionContext> {
            TransactionContext(resolve())
        }
    }
}
