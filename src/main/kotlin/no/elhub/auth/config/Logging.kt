package no.elhub.auth.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level

fun Application.configureLogging() {
    val excludedPaths = listOf("/health", "/metrics")
    install(CallLogging) {
        level = Level.INFO
        filter { call ->
            excludedPaths.none { prefix -> call.request.path().startsWith(prefix) }
        }
        format { call ->
            val status = call.response.status()?.value.toString()
            val method = call.request.httpMethod.value
            val uri = call.request.path()
            val userAgent = call.request.headers["User-Agent"].orEmpty()
            "status=$status method=$method uri=$uri userAgent=$userAgent"
        }
    }
}
