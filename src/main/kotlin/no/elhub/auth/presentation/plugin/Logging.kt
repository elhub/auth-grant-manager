package no.elhub.auth.presentation.plugin

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level

fun Application.configureLogging() {
    install(CallLogging) {
        /* Configure call logging to deliver Logfmt formatted logs */
        level = Level.INFO
        format { call ->
            val status = call.response.status()?.value.toString()
            val method = call.request.httpMethod.value
            val uri = call.request.path()
            val userAgent = call.request.headers["User-Agent"].orEmpty()
            "status=$status method=$method uri=$uri userAgent=$userAgent"
        }
    }
}
