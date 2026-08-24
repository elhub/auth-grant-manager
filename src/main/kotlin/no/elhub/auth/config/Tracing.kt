package no.elhub.auth.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import no.elhub.auth.features.common.ELHUB_TRACE_ID_HEADER
import java.util.UUID

fun Application.configureRequestTracing() {
    install(CallId) {
        header(ELHUB_TRACE_ID_HEADER)
        verify { callId ->
            try {
                UUID.fromString(callId)
                true
            } catch (_: IllegalArgumentException) {
                throw InvalidTraceIdException()
            }
        }
        generate { UUID.randomUUID().toString() }
    }
}
