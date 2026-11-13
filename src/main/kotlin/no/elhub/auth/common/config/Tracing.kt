package no.elhub.auth.common.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.request.path
import no.elhub.auth.common.ELHUB_TRACE_ID_HEADER
import no.elhub.auth.common.InvalidTraceIdException
import no.elhub.auth.common.V1_ELHUB_TRACE_ID_HEADER
import java.util.UUID

private const val V1_ACCESS_PATH_PREFIX = "/access/v1/"

fun Application.configureRequestTracing() {
    install(CallId) {
        // Internal services should send a trace ID for end-to-end tracing. Accept both the v1
        // Elhub-Trace-Id and legacy v0 ElhubTraceId headers for now, while services migrate to v1.
        retrieve { call ->
            call.request.headers[V1_ELHUB_TRACE_ID_HEADER] ?: call.request.headers[ELHUB_TRACE_ID_HEADER]
        }
        reply { call, callId -> call.response.headers.append(responseTraceIdHeaderForPath(call.request.path()), callId) }
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

// Use the Elhub-Trace-Id format defined by EVEREST for v1 responses, but keep the legacy
// ElhubTraceId response header for v0 rather than introducing the new format there.
private fun responseTraceIdHeaderForPath(path: String): String =
    if (path.startsWith(V1_ACCESS_PATH_PREFIX)) V1_ELHUB_TRACE_ID_HEADER else ELHUB_TRACE_ID_HEADER
