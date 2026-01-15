package no.elhub.auth.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import no.elhub.auth.features.openapi.API_PATH_OPENAPI
import org.slf4j.event.Level

fun Application.configureLogging() {
    val excludedPaths = listOf(
        "/health",
        "/metrics",
        "/$API_PATH_OPENAPI",
    )
    install(CallLogging) {
        level = Level.INFO
        callIdMdc("traceId")
        filter { call ->
            excludedPaths.none { prefix -> call.request.path().startsWith(prefix) }
        }
        format { call ->
            val status = call.response.status()?.value.toString()
            val method = call.request.httpMethod.value
            val uri = call.request.path()
            val userAgent = call.request.headers["User-Agent"].orEmpty()
            val durationMs = call.processingTimeMillis()
            "status=$status method=$method uri=$uri userAgent=$userAgent durationMs=$durationMs"
        }
    }
}
