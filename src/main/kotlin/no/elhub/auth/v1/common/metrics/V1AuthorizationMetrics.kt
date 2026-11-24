package no.elhub.auth.v1.common.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.elhub.auth.common.config.withTransaction
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationRequestType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

const val REQUESTS_METRIC = "access_v1_authorization_requests"
const val DOCUMENTS_METRIC = "access_v1_authorization_documents"
const val LAST_SUCCESS_METRIC = "access_v1_authorization_metrics_last_success_timestamp_seconds"
private const val REFRESH_METRIC = "access_v1_authorization_metrics_refresh"
private const val QUERY_TIMEOUT = "5s"

enum class MetricState(val label: String) {
    Created("created"),
    Approved("approved"),
    Rejected("rejected"),
}

class V1AuthorizationMetrics(registry: MeterRegistry) {
    private val logger = LoggerFactory.getLogger(V1AuthorizationMetrics::class.java)
    private val refreshTimer = registry.timer(REFRESH_METRIC)
    private val lastSuccess = AtomicReference(Double.NaN).also { value ->
        Gauge.builder(LAST_SUCCESS_METRIC, value) { it.get() }
            .description("Unix timestamp of the last successful authorization metrics refresh")
            .register(registry)
    }

    private val requests = ResourceMetric(
        registry = registry,
        name = REQUESTS_METRIC,
        types = AuthorizationRequestType.entries,
        states = listOf(MetricState.Created, MetricState.Approved, MetricState.Rejected),
        countsSql = """
            SELECT request_type::text AS type,
                   count(*) AS created,
                   count(*) FILTER (WHERE approved_at IS NOT NULL) AS approved,
                   count(*) FILTER (WHERE status = 'Rejected') AS rejected
            FROM auth_v1.authorization_request
            GROUP BY request_type
        """,
    )

    private val documents = ResourceMetric(
        registry = registry,
        name = DOCUMENTS_METRIC,
        types = AuthorizationDocumentType.entries,
        states = listOf(MetricState.Created, MetricState.Approved),
        countsSql = """
            SELECT document_type::text AS type,
                   count(*) AS created,
                   count(*) FILTER (WHERE signed_at IS NOT NULL) AS approved
            FROM auth_v1.authorization_document
            GROUP BY document_type
        """,
    )

    suspend fun refresh() {
        val start = System.nanoTime()
        try {
            withTransaction {
                exec("SET TRANSACTION READ ONLY")
                exec("SET LOCAL statement_timeout = '$QUERY_TIMEOUT'")
                requests.refresh(this)
                documents.refresh(this)
            }
            lastSuccess.set(System.currentTimeMillis() / 1000.0)
        } finally {
            refreshTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
        }
    }

    fun start(scope: CoroutineScope, interval: Duration) = scope.launch {
        while (isActive) {
            runCatching { refresh() }.onFailure { logger.warn("Failed to refresh v1 authorization metrics", it) }
            delay(interval)
        }
    }
}

/**
 * One gauge per (type, state). [countsSql] must return a `type` column and one column per state, named by [MetricState.label].
 */
private class ResourceMetric<T : Enum<T>>(
    registry: MeterRegistry,
    name: String,
    types: List<T>,
    private val states: List<MetricState>,
    private val countsSql: String,
) {
    private val gauges: Map<T, Map<MetricState, AtomicReference<Double>>> = types.associateWith { type ->
        states.associateWith { state ->
            AtomicReference(Double.NaN).also { value ->
                Gauge.builder(name, value) { it.get() }
                    .tag("type", type.name)
                    .tag("state", state.label)
                    .register(registry)
            }
        }
    }

    fun refresh(transaction: JdbcTransaction) {
        val countsByType = mutableMapOf<String, Map<MetricState, Long>>()
        transaction.exec(countsSql) { rs ->
            while (rs.next()) {
                countsByType[rs.getString("type")] = states.associateWith { rs.getLong(it.label) }
            }
        }

        gauges.forEach { (type, gaugesByState) ->
            gaugesByState.forEach { (state, gauge) -> gauge.set(countsByType[type.name]?.get(state)?.toDouble() ?: 0.0) }
        }
    }
}
