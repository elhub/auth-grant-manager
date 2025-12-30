package no.elhub.auth.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import java.util.UUID

fun Application.configureRequestTracing() {
    install(CallId) {
        retrieve { null }
        generate { UUID.randomUUID().toString() }
        replyToHeader("Elhub-Trace-Id")
    }
}
