package no.elhub.auth.config

import arrow.core.Either
import arrow.core.flatMap
import com.sksamuel.cohort.Cohort
import com.sksamuel.cohort.HealthCheck
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.HealthCheckResult
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import com.sksamuel.cohort.hikari.HikariDataSourceManager
import com.sksamuel.cohort.micrometer.CohortMetrics
import com.sksamuel.cohort.threads.ThreadDeadlockHealthCheck
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respond
import io.ktor.client.request.get as sendGetRequest
import io.ktor.server.routing.get as buildGetRoute
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.minutes
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.server.config.ApplicationConfig
import kotlin.time.Duration.Companion.seconds

fun Application.configureMonitoring(
    dataSource: HikariDataSource,
) {
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
    }
    val pdpHealthUrl = "${environment.config.property("pdp.baseUrl").getString()}/health"
    val authPersonsHealthUrl = "${environment.config.property("authPersons.baseUri").getString()}/health"
    val meteringPointsHealthUrl =
        "${environment.config.property("structureData.meteringPointsService.serviceUrl").getString()}/health"
    val organisationsServiceHealthUrl =
        "${environment.config.property("structureData.organisationsService.serviceUrl").getString()}/health"
    install(Cohort) {
        dataSources = listOf(HikariDataSourceManager(dataSource))
        sysprops = true

        val dependencyCheckClient = HttpClient()
        val checks = HealthCheckRegistry(Dispatchers.Default) {
            register("Thread Deadlocks", ThreadDeadlockHealthCheck(), 10.seconds, 1.minutes)
            register("Database Connection", HikariConnectionsHealthCheck(dataSource, 1), 10.seconds, 5.seconds)
            register(
                "PDP connection",
                ServiceDependencyHealthCheck(dependencyCheckClient, pdpHealthUrl),
                10.seconds,
                5.seconds,
            )
            register(
                "Auth persons connection", ServiceDependencyHealthCheck(dependencyCheckClient, authPersonsHealthUrl),
                10.seconds,
                5.seconds,
            )
            register(
                "Metering points service connection",
                ServiceDependencyHealthCheck(dependencyCheckClient, meteringPointsHealthUrl),
                10.seconds,
                5.seconds,
            )
            register(
                "Organisations service connection",
                ServiceDependencyHealthCheck(dependencyCheckClient, organisationsServiceHealthUrl),
                10.seconds,
                5.seconds,
            )
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

class ServiceDependencyHealthCheck(
    private val client: HttpClient,
    private val url: String,
) : HealthCheck {
    override suspend fun check(): HealthCheckResult = Either.catch {
        client.sendGetRequest(url) {
            timeout {
                requestTimeoutMillis = 1500
            }
        }
    }.mapLeft { "Failed calling $url: ${it.message}" }
        .flatMap { response ->
            if (response.status.isSuccess()) {
                Either.Right("$url OK")
            } else {
                Either.Left(
                    "HTTP ${response.status.value} from $url with message ${response.bodyAsText()}"
                )
            }
        }
        .fold(
            { HealthCheckResult.unhealthy(it) },
            { HealthCheckResult.healthy(it) }
        )
}

