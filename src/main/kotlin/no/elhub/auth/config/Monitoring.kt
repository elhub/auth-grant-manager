package no.elhub.auth.config

import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import com.sksamuel.cohort.hikari.HikariDataSourceManager
import com.sksamuel.cohort.micrometer.CohortMetrics
import com.sksamuel.cohort.threads.ThreadDeadlockHealthCheck
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun Application.configureMonitoring(dataSource: HikariDataSource) {
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
    }

    install(Cohort) {
        dataSources = listOf(HikariDataSourceManager(dataSource))
        sysprops = true

        val checks = HealthCheckRegistry(Dispatchers.Default) {
            register("Thread Deadlocks", ThreadDeadlockHealthCheck(), 1.minutes, 1.minutes)
            register("Database Connection", HikariConnectionsHealthCheck(dataSource, 1), 15.seconds, 5.seconds)
        }
        healthcheck("/health", checks)
        CohortMetrics(checks).bindTo(appMicrometerRegistry)
    }

    routing {
        get("/metrics") {
            call.respond(appMicrometerRegistry.scrape())
        }
    }
}
