package no.elhub.auth.features.common

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.elhub.auth.features.common.auth.AUTHINFO_POLICY_ROUTE
import org.testcontainers.containers.GenericContainer

class PdpTestContainerExtension() : BeforeSpecListener, AfterSpecListener {

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

    suspend fun registerEnduserMapping(token: String, partyId: String) {
        val client = HttpClient(Apache5)
        client.post("http://localhost:8085/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "priority": 1,
                  "request": {
                    "method": "POST",
                    "url": "$AUTHINFO_POLICY_ROUTE",
                    "bodyPatterns": [
                      { "contains": "\"token\":\"$token\"" }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "application/json" },
                    "jsonBody": {
                      "result": {
                        "authInfo": {
                          "actingId": "$partyId",
                          "actingType": "person",
                          "originalId": "$partyId"
                        },
                        "tokenInfo": {
                          "partyId": "$partyId",
                          "tokenStatus": "verified",
                          "tokenType": "enduser"
                        }
                      }
                    }
                  }
                }
                """.trimIndent()
            )
        }
        client.close()
    }

    suspend fun registerMaskinportenMapping(token: String, actingGln: String, functionName: String) {
        val client = HttpClient(Apache5)
        client.post("http://localhost:8085/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "priority": 1,
                  "request": {
                    "method": "POST",
                    "url": "$AUTHINFO_POLICY_ROUTE",
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
                          "authorizedFunctions": [
                            {
                              "functionCode": "SELF",
                              "functionName": "$functionName"
                            }
                          ],
                          "actingGLN": "$actingGln"
                        }
                      }
                    }
                  }
                }
                """.trimIndent()
            )
        }
        client.close()
    }

    suspend fun registerElhubServiceTokenMapping(token: String, partyId: String) {
        val client = HttpClient(Apache5)
        client.post("http://localhost:8085/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "priority": 1,
                  "request": {
                    "method": "POST",
                    "url": "$AUTHINFO_POLICY_ROUTE",
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
            )
        }
        client.close()
    }

    suspend fun registerInvalidTokenMapping() {
        val client = HttpClient(Apache5)
        client.post("http://localhost:8085/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "request": {
                    "method": "POST",
                    "url": "$AUTHINFO_POLICY_ROUTE",
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
                """.trimIndent()
            )
        }
        client.close()
    }

    suspend fun registerEnduserWithOrganisationAuthInfoMapping(
        token: String,
        actingId: String,
        actingOrganisationNumber: String,
        originalId: String,
    ) {
        val client = HttpClient(Apache5)
        client.post("http://localhost:8085/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "priority": 1,
                  "request": {
                    "method": "POST",
                    "url": "$AUTHINFO_POLICY_ROUTE",
                    "bodyPatterns": [
                      { "contains": "\"token\":\"$token\"" }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "application/json" },
                    "jsonBody": {
                      "result": {
                        "authInfo": {
                          "actingId": "$actingId",
                          "actingOrganisationNumber": "$actingOrganisationNumber",
                          "actingType": "organisation",
                          "originalId": "$originalId"
                        },
                        "tokenInfo": {
                          "partyId": "$originalId",
                          "tokenStatus": "verified",
                          "tokenType": "enduser"
                        }
                      }
                    }
                  }
                }
                """.trimIndent()
            )
        }
        client.close()
    }

    suspend fun registerPdpUnparseableResponseMapping(token: String) {
        val client = HttpClient(Apache5)
        client.post("http://localhost:8085/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "priority": 1,
                  "request": {
                    "method": "POST",
                    "url": "$AUTHINFO_POLICY_ROUTE",
                    "bodyPatterns": [
                      { "contains": "\"token\":\"$token\"" }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "application/json" },
                    "body": "this is not valid json {"
                  }
                }
                """.trimIndent()
            )
        }
        client.close()
    }

    suspend fun registerPdpHttpErrorMapping(token: String, statusCode: Int = 500) {
        val client = HttpClient(Apache5)
        client.post("http://localhost:8085/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "priority": 1,
                  "request": {
                    "method": "POST",
                    "url": "$AUTHINFO_POLICY_ROUTE",
                    "bodyPatterns": [
                      { "contains": "\"token\":\"$token\"" }
                    ]
                  },
                  "response": {
                    "status": $statusCode,
                    "headers": { "Content-Type": "application/json" },
                    "body": "Internal Server Error"
                  }
                }
                """.trimIndent()
            )
        }
        client.close()
    }

    suspend fun registerPdpBodyErrorMapping(
        token: String,
        errorCode: String = "INTERNAL_ERROR",
        errorMessage: String = "An internal PDP error occurred"
    ) {
        val client = HttpClient(Apache5)
        client.post("http://localhost:8085/__admin/mappings") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "priority": 1,
                  "request": {
                    "method": "POST",
                    "url": "$AUTHINFO_POLICY_ROUTE",
                    "bodyPatterns": [
                      { "contains": "\"token\":\"$token\"" }
                    ]
                  },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "application/json" },
                    "jsonBody": {
                      "result": {
                        "error": {
                          "code": "$errorCode",
                          "message": "$errorMessage"
                        }
                      }
                    }
                  }
                }
                """.trimIndent()
            )
        }
        client.close()
    }

    suspend fun getLastRequestBody(): String {
        val client = HttpClient(Apache5)
        val response: String = client.get("http://localhost:8085/__admin/requests?limit=1").body()
        client.close()
        return response
    }

    override suspend fun beforeSpec(spec: Spec) {
        container.start()
    }

    override suspend fun afterSpec(spec: Spec) {
        container.stop()
    }
}
