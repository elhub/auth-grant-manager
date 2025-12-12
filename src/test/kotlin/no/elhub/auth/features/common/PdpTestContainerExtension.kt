package no.elhub.auth.features.common

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.testcontainers.containers.GenericContainer

class PdpTestContainerExtension(val alwaysReturn: String) : BeforeSpecListener, AfterSpecListener {
    private val client = HttpClient(CIO)
    private val container = GenericContainer("wiremock/wiremock:3.13.2").apply {
        withExposedPorts(8080)
        withCommand("--verbose")
        withCreateContainerCmdModifier { cmd ->
            cmd.withHostConfig(
                HostConfig().withPortBindings(
                    PortBinding(Ports.Binding.bindPort(8085), ExposedPort(8080))
                )
            )
        }
    }

    private fun baseUrl(): String = "http://${container.host}:${container.getMappedPort(8080)}"

    override suspend fun beforeSpec(spec: Spec) {
        container.start()
        val base = baseUrl()
        val mapping = """
      {
        "request": { "method": "POST", "url": "/v1/data/v2/token/authinfo" },
        "response": {
          "status": 200,
          "headers": { "Content-Type": "application/json" },
          "jsonBody": ${alwaysReturn.trimIndent()}
        }
      }
        """.trimIndent()

        client.post("$base/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(
                mapping.trimIndent()
            )
        }
    }

    override suspend fun afterSpec(spec: Spec) {
        container.stop()
    }
}
