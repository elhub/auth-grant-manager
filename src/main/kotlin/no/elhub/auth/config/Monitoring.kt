package no.elhub.auth.config

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheck
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.HealthCheckResult
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import com.sksamuel.cohort.hikari.HikariDataSourceManager
import com.sksamuel.cohort.micrometer.CohortMetrics
import com.sksamuel.cohort.threads.ThreadDeadlockHealthCheck
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.request.get as sendGetRequest
import io.ktor.server.routing.get as buildGetRoute

fun Application.configureMonitoring(dataSource: HikariDataSource) {
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
    }
    val serviceDependencies = listOf(
        ServiceDependency("PDP", "pdp.baseUrl"),
        ServiceDependency("Auth persons", "authPersons.baseUri"),
        ServiceDependency("Metering points service", "structureData.meteringPointsService.baseUrl"),
        ServiceDependency("Organisations service", "structureData.organisationsService.baseUrl"),
        // TODO consider IDP
    )
    install(Cohort) {
        dataSources = listOf(HikariDataSourceManager(dataSource))
        sysprops = true

        val dependencyCheckClient = HttpClient()
        val checks = HealthCheckRegistry(Dispatchers.Default) {
            register("Thread Deadlocks", ThreadDeadlockHealthCheck(), 10.seconds, 1.minutes)
            register(
                "Database Connection",
                HikariConnectionsHealthCheck(dataSource, 1),
                10.seconds,
                5.seconds
            )
            serviceDependencies.forEach { dependency ->
                val url =
                    environment.config.property(dependency.baseUrlConfigKey).getString() + "/health"
                register(
                    "${dependency.name} connection",
                    ServiceDependencyHealthCheck(dependencyCheckClient, url),
                    10.seconds,
                    10.seconds
                )
            }
        }
        healthcheck("/health", checks)
        CohortMetrics(checks).bindTo(appMicrometerRegistry)
    }

    routing {
        buildGetRoute("/metrics") {
            call.respond(appMicrometerRegistry.scrape())
        }
    }
}

private data class ServiceDependency(
    val name: String,
    val baseUrlConfigKey: String,
)

private class ServiceDependencyHealthCheck(
    private val client: HttpClient,
    private val url: String,
) : HealthCheck {
    override suspend fun check(): HealthCheckResult = either {
        val response = Either.catch { client.sendGetRequest(url) }.bind()

        ensure(response.status.isSuccess()) {
            "$url responded with ${response.status.value}: ${response.bodyAsText()}"
        }

        println("$url OK :)")

        "$url OK"
    }.fold(
        { HealthCheckResult.unhealthy("Failed calling $url: $it") },
        { HealthCheckResult.healthy(it) }
    )
}
