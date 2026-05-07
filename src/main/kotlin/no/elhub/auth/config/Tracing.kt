package no.elhub.auth.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import java.util.UUID

private const val ELHUB_TRACE_ID_HEADER = "ElhubTraceID"
private const val ELHUB_TRACE_ID_HEADER_DEPRECATED = "Elhub-Trace-Id"

fun Application.configureRequestTracing() {
    install(CallId) {
        retrieve { call ->
            call.request.headers[ELHUB_TRACE_ID_HEADER]?.ifBlank { null }
        }
        generate { UUID.randomUUID().toString() }
        // Reply with both headers until Marked has been informed about the response header name change.
        replyToHeader(ELHUB_TRACE_ID_HEADER)
        replyToHeader(ELHUB_TRACE_ID_HEADER_DEPRECATED)
    }
}
