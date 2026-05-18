package no.elhub.auth.features.common

import org.slf4j.MDC

const val ELHUB_TRACE_ID_HEADER = "ElhubTraceID"
const val TRACE_ID_MDC_KEY = "traceId"

object TraceIdContext {
    fun currentOrNull(): String? = MDC.get(TRACE_ID_MDC_KEY)?.takeIf { it.isNotBlank() }
}
