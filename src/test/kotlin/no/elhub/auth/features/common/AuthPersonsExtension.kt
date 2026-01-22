package no.elhub.auth.features.common

import io.kotest.core.listeners.AfterProjectListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.UUID

object AuthPersonsTestContainer {

    private val image = DockerImageName.parse("wiremock/wiremock:3.13.2")
    private var container: GenericContainer<*>? = null

    fun start() {
        if (container != null) return
        container = GenericContainer(image).apply {
            withExposedPorts(8080)
            withCommand("--verbose", "--global-response-templating")
            waitingFor(
                Wait.forHttp("/__admin/health")
                    .forStatusCodeMatching { it == 200 }
                    .withStartupTimeout(Duration.ofSeconds(30))
            )
            start()
        }
        runBlocking { registerDefaultPersonMapping() }
    }

    fun stop() {
        container?.runCatching { stop() }
        container = null
    }

    fun baseUri(): String {
        val c = container ?: error("AuthPersonsTestContainer not started")
        return "http://${c.host}:${c.getMappedPort(8080)}"
    }

    suspend fun registerPersonMapping(nin: String, personId: UUID) {
        val client = HttpClient(CIO)
        client.post("${baseUri()}/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(personMapping(nin, personId))
        }
        client.close()
    }

    private suspend fun registerDefaultPersonMapping() {
        val client = HttpClient(CIO)
        client.post("${baseUri()}/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(defaultPersonMapping())
        }
        client.close()
    }

    private fun personMapping(nin: String, personId: UUID): String =
        """
        {
          "priority": 1,
          "request": {
            "method": "POST",
            "url": "/persons",
            "headers": {
              "Content-Type": { "matches": "application/json.*" }
            },
            "bodyPatterns": [
              { "matchesJsonPath": { "expression": "${'$'}.data.type", "equalTo": "Person" } },
              { "matchesJsonPath": { "expression": "${'$'}.data.attributes.nationalIdentityNumber", "equalTo": "$nin" } }
            ]
          },
          "response": {
            "status": 200,
            "headers": { "Content-Type": "application/json" },
            "jsonBody": {
              "data": { "type": "Person", "id": "$personId" },
              "links": { "self": "/persons" }
            }
          }
        }
        """.trimIndent()

    private fun defaultPersonMapping(): String =
        """
        {
          "priority": 10,
          "request": {
            "method": "POST",
            "url": "/persons",
            "headers": {
              "Content-Type": { "matches": "application/json.*" }
            },
            "bodyPatterns": [
              { "matchesJsonPath": { "expression": "${'$'}.data.type", "equalTo": "Person" } },
              { "matchesJsonPath": { "expression": "${'$'}.data.attributes.nationalIdentityNumber", "matches": "^[0-9]{11}$" } }
            ]
          },
          "response": {
            "status": 200,
            "headers": { "Content-Type": "application/json" },
            "transformers": ["response-template"],
            "body": "{ \"data\": { \"type\": \"Person\", \"id\": \"{{randomValue type='UUID'}}\" }, \"links\": { \"self\": \"/persons\" } }"
          }
        }
        """.trimIndent()
}

object AuthPersonsTestContainerExtension : BeforeSpecListener {
    override suspend fun beforeSpec(spec: Spec) = AuthPersonsTestContainer.start()
}

object StopAuthPersonsTestContainerExtension : AfterProjectListener {
    override suspend fun afterProject() = AuthPersonsTestContainer.stop()
}
