package no.elhub.auth.v1.acceptance.support

import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.server.config.yaml.YamlConfig
import io.ktor.server.testing.TestApplication

/**
 * Starts the application exactly as `EngineMain` does in a deployed environment:
 * the real `application.yaml`, with the real `ktor.application.modules` list and real dependency wiring.
 *
 * Only the environment variables differ. They are passed as system properties, which the YAML config
 * resolves the same way as environment variables. Any variable missing from
 * [AcceptanceTestInfrastructure.environmentVariables] fails the startup, so a new integration cannot be
 * added without also giving it a local fake.
 */
class ProductionApp private constructor(
    private val app: TestApplication,
    private val previousProperties: Map<String, String?>,
) {
    val client: HttpClient = app.createClient {
        defaultRequest { headers.append(HttpHeaders.UserAgent, "auth-grant-manager-acceptance-test") }
        expectSuccess = false
    }

    suspend fun stop() {
        try {
            app.stop()
        } finally {
            previousProperties.forEach { (name, value) ->
                if (value == null) System.clearProperty(name) else System.setProperty(name, value)
            }
        }
    }

    companion object {
        suspend fun start(): ProductionApp {
            val variables = AcceptanceTestInfrastructure.environmentVariables
            val previous = variables.keys.associateWith(System::getProperty)
            try {
                variables.forEach { (name, value) -> System.setProperty(name, value) }
                val config = checkNotNull(YamlConfig("application.yaml")) { "application.yaml not found on the classpath" }
                val app = TestApplication { environment { this.config = config } }
                try {
                    app.start()
                } catch (failure: Throwable) {
                    app.stop()
                    throw failure
                }
                return ProductionApp(app, previous)
            } catch (failure: Throwable) {
                previous.forEach { (name, value) ->
                    if (value == null) System.clearProperty(name) else System.setProperty(name, value)
                }
                throw failure
            }
        }
    }
}
