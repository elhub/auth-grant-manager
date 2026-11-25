package no.elhub.auth.common.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import no.elhub.auth.common.ELHUB_TRACE_ID_HEADER
import no.elhub.auth.common.InvalidTraceIdException
import no.elhub.auth.common.V1_ELHUB_TRACE_ID_HEADER
import java.util.UUID

fun Application.configureRequestTracing() {
    install(CallId) {
        // Internal services should send a trace ID for end-to-end tracing.
        // v1 only accepts Elhub-Trace-Id; v0 keeps accepting both header names.
        retrieve { call ->
            call.request.headers[V1_ELHUB_TRACE_ID_HEADER]
                ?: if (call.isV1) null else call.request.headers[ELHUB_TRACE_ID_HEADER]
        }
        reply { call, callId ->
            call.response.headers.append(if (call.isV1) V1_ELHUB_TRACE_ID_HEADER else ELHUB_TRACE_ID_HEADER, callId)
        }
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
