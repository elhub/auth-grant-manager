package no.elhub.devxp.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging

fun Application.configureLogging() {
    install(CallLogging)
}
