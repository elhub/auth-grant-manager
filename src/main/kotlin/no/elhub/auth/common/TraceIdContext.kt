package no.elhub.auth.common

import org.slf4j.MDC

const val ELHUB_TRACE_ID_HEADER = "ElhubTraceId"
const val V1_ELHUB_TRACE_ID_HEADER = "Elhub-Trace-Id"
const val TRACE_ID_MDC_KEY = "traceId"

object TraceIdContext {
    fun currentOrNull(): String? = MDC.get(TRACE_ID_MDC_KEY)?.takeIf { it.isNotBlank() }
}
