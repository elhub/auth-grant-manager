package no.elhub.auth.v0.config

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

fun Application.configModule() {
    dependencies {
        provide<TransactionContext> {
            TransactionContext(resolve())
        }
    }
}
