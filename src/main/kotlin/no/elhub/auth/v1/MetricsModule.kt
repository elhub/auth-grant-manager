package no.elhub.auth.v1

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.elhub.auth.v1.common.metrics.V1AuthorizationMetrics
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun Application.metricsModule() {
    if (!environment.config.propertyOrNull("v1.enabled")?.getString().toBoolean()) {
        return
    }

    val registry: PrometheusMeterRegistry by dependencies
    val interval = environment.config.propertyOrNull("v1.metrics.refreshInterval")?.getString()?.let(Duration::parse)
        ?: 5.minutes

    V1AuthorizationMetrics(registry).start(this, interval)
}
