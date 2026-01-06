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

class PdpTestContainerExtension(
    private val maskinportenResponse: String = DEFAULT_MASKINPORTEN_RESPONSE,
    private val enduserResponse: String = DEFAULT_ENDUSER_RESPONSE,
    private val elhubServiceResponse: String = DEFAULT_ELHUB_SERVICE_RESPONSE
) : BeforeSpecListener, AfterSpecListener {

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

        client.post("$base/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(maskinportenMapping(maskinportenResponse))
        }

        client.post("$base/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(enduserMapping(enduserResponse))
        }

        client.post("$base/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(elhubServiceMapping(elhubServiceResponse))
        }
    }

    override suspend fun afterSpec(spec: Spec) {
        container.stop()
    }

    private fun maskinportenMapping(body: String): String =
        """
      {
        "priority": 1,
        "request": {
          "method": "POST",
          "url": "/v1/data/v2/token/authinfo",
          "bodyPatterns": [
            { "matchesJsonPath": "$.input.payload.SenderGLN" }
          ]
        },
        "response": {
          "status": 200,
          "headers": { "Content-Type": "application/json" },
          "jsonBody": ${body.trimIndent()}
        }
      }
        """.trimIndent()

    private fun enduserMapping(body: String): String =
        """
      {
        "priority": 10,
        "request": {
          "method": "POST",
          "url": "/v1/data/v2/token/authinfo"
        },
        "response": {
          "status": 200,
          "headers": { "Content-Type": "application/json" },
          "jsonBody": ${body.trimIndent()}
        }
      }
        """.trimIndent()

    private fun elhubServiceMapping(body: String): String =
        """
      {
        "priority": 5,
        "request": {
          "method": "POST",
          "url": "/v1/data/v2/token/authinfo",
          "bodyPatterns": [
            { "contains": "\"token\":\"elhub-service\"" }
          ]
        },
        "response": {
          "status": 200,
          "headers": { "Content-Type": "application/json" },
          "jsonBody": ${body.trimIndent()}
        }
      }
        """.trimIndent()

    companion object {
        suspend fun registerMaskinportenMapping(senderGln: String, actingGln: String) {
            val client = HttpClient(CIO)
            client.post("http://localhost:8085/__admin/mappings") {
                contentType(ContentType.Application.Json)
                setBody(maskinportenMapping(senderGln, actingGln))
            }
            client.close()
        }

        suspend fun registerEnduserMapping(token: String, partyId: String) {
            val client = HttpClient(CIO)
            client.post("http://localhost:8085/__admin/mappings") {
                contentType(ContentType.Application.Json)
                setBody(enduserMapping(token, partyId))
            }
            client.close()
        }

        private fun maskinportenMapping(senderGln: String, actingGln: String): String =
            """
        {
          "priority": 1,
          "request": {
            "method": "POST",
            "url": "/v1/data/v2/token/authinfo",
            "bodyPatterns": [
              { "matchesJsonPath": "$[?(@.input.payload.SenderGLN == \"$senderGln\")]" }
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
                  "actingFunction": "BalanceSupplier",
                  "actingGLN": "$actingGln"
                }
              }
            }
          }
        }
            """.trimIndent()

        private fun enduserMapping(token: String, partyId: String): String =
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
                    "jsonBody": ${enduserResponse(partyId)}
                  }
                }
            """.trimIndent()

        private fun enduserResponse(partyId: String): String =
            """
                {
                  "result": {
                    "tokenInfo": {
                      "tokenStatus": "verified",
                      "partyId": "$partyId",
                      "tokenType": "enduser"
                    }
                  }
                }
            """.trimIndent()

        private val DEFAULT_MASKINPORTEN_RESPONSE = """
        {
          "result": {
            "tokenInfo": {
              "tokenStatus": "verified",
              "partyId": "maskinporten",
              "tokenType": "maskinporten"
            },
            "authInfo": {
              "actingFunction": "BalanceSupplier",
              "actingGLN": "0107000000021"
            }
          }
        }
        """.trimIndent()

        private val DEFAULT_ENDUSER_RESPONSE = """
        {
          "result": {
            "tokenInfo": {
              "tokenStatus": "verified",
              "partyId": "17abdc56-8f6f-440a-9f00-b9bfbb22065e",
              "tokenType": "enduser"
            }
          }
        }
        """.trimIndent()

        private val DEFAULT_ELHUB_SERVICE_RESPONSE = """
        {
          "result": {
            "tokenInfo": {
              "tokenStatus": "verified",
              "partyId": "${Constants.CONSENT_MANAGEMENT_OSB_ID}",
              "tokenType": "elhub-service"
            }
          }
        }
        """.trimIndent()
    }
}
