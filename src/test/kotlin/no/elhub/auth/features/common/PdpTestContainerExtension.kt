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

class PdpTestContainerExtension :
    BeforeSpecListener,
    AfterSpecListener {
    private val container =
        GenericContainer("wiremock/wiremock:3.13.2").apply {
            withExposedPorts(8080)
            withCommand("--verbose")
            withCreateContainerCmdModifier { cmd ->
                cmd.withHostConfig(
                    HostConfig().withPortBindings(
                        PortBinding(Ports.Binding.bindPort(8085), ExposedPort(8080)),
                    ),
                )
            }
        }

    suspend fun registerEnduserMapping(
        token: String,
        partyId: String,
    ) {
        val client = HttpClient(CIO)
        client.post("http://localhost:8085/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(enduserMapping(token, partyId))
        }
        client.close()
    }

    suspend fun registerMaskinportenMapping(
        token: String,
        actingGln: String,
        actingFunction: String,
    ) {
        val client = HttpClient(CIO)
        client.post("http://localhost:8085/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(maskinportenMapping(token, actingGln, actingFunction))
        }
        client.close()
    }

    suspend fun registerElhubServiceTokenMapping(
        token: String,
        partyId: String,
    ) {
        val client = HttpClient(CIO)
        client.post("http://localhost:8085/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(elhubServiceMapping(token, partyId))
        }
        client.close()
    }

    suspend fun registerInvalidTokenMapping() {
        val client = HttpClient(CIO)
        client.post("http://localhost:8085/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "request": {
                    "method": "POST",
                    "url": "/v1/data/v2/token/authinfo",
                    "bodyPatterns": [
                      { "contains": "\"token\":\"invalid-token\"" }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "application/json" },
                    "jsonBody": {
                      "result": {
                        "tokenInfo": {
                          "tokenStatus": "invalid"
                        }
                      }
                    }
                  }
                }
                """.trimIndent(),
            )
        }
    }

    override suspend fun beforeSpec(spec: Spec) {
        container.start()
    }

    override suspend fun afterSpec(spec: Spec) {
        container.stop()
    }

    companion object {
        private fun maskinportenMapping(
            token: String,
            actingGln: String,
            actingFunction: String,
        ): String =
            """
            {
              "priority": 1,
              "request": {
                "method": "POST",
                "url": "/v1/data/v2/token/authinfo",
                "bodyPatterns": [
                  { "contains": "\"token\":\"$token\"" }
                ]
              },
              "response": {
                "status": 200,
                "headers": { "Content-Type": "application/json" },
                "jsonBody": {
                  "result": {
                    "tokenInfo": {
                      "tokenStatus": "verified",
                      "partyId": "maskinporten",
                      "tokenType": "maskinporten"
                    },
                    "authInfo": {
                      "actingFunction": "$actingFunction",
                      "actingGLN": "$actingGln"
                    }
                  }
                }
              }
            }
            """.trimIndent()

        private fun enduserMapping(
            token: String,
            partyId: String,
        ): String =
            """
            {
              "priority": 1,
              "request": {
                "method": "POST",
                "url": "/v1/data/v2/token/authinfo",
                "bodyPatterns": [
                  { "contains": "\"token\":\"$token\"" }
                ]
              },
              "response": {
                "status": 200,
                "headers": { "Content-Type": "application/json" },
                "jsonBody": {
                 "result": {
                  "tokenInfo": {
                   "tokenStatus": "verified",
                   "partyId": "$partyId",
                   "tokenType": "enduser"
                  }
                 }
                }
              }
            }
            """.trimIndent()

        private fun elhubServiceMapping(
            token: String,
            partyId: String,
        ): String =
            """
            {
              "priority": 1,
              "request": {
                "method": "POST",
                "url": "/v1/data/v2/token/authinfo",
                "bodyPatterns": [
                  { "contains": "\"token\":\"$token\"" }
                ]
              },
              "response": {
                "status": 200,
                "headers": { "Content-Type": "application/json" },
                "jsonBody": {
                 "result": {
                  "tokenInfo": {
                   "tokenStatus": "verified",
                   "partyId": "$partyId",
                   "tokenType": "elhub-service"
                  }
                 }
                }
              }
            }
            """.trimIndent()
    }
}
