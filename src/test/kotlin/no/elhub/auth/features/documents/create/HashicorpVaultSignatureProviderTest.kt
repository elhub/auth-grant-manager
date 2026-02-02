package no.elhub.auth.features.documents.create

import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.util.Base64

class HashicorpVaultSignatureProviderTest :
    FunSpec({
        test("sends correct vault signing parameters in request body") {
            val json = Json { ignoreUnknownKeys = true }
            val tokenFile = Files.createTempFile("vault-token", ".txt")
            Files.writeString(tokenFile, "test-token")

            val expectedSignature = "sig".encodeToByteArray()
            val expectedSignatureB64 = Base64.getEncoder().encodeToString(expectedSignature)

            val client =
                HttpClient(
                    MockEngine { request ->
                        request.headers["X-Vault-Token"] shouldBe "test-token"
                        request.url.toString() shouldBe "http://vault.test/v1/transit/sign/test-key"

                        val bodyText = readBodyText(request.body)
                        val payload = json.decodeFromString<JsonObject>(bodyText)

                        payload["input"]?.jsonPrimitive?.content shouldBe
                            Base64
                                .getEncoder()
                                .encodeToString("data".encodeToByteArray())
                        payload["hash_algorithm"]?.jsonPrimitive?.content shouldBe "sha2-256"
                        payload["signature_algorithm"]?.jsonPrimitive?.content shouldBe "pkcs1v15"
                        payload["prehashed"]?.jsonPrimitive?.content shouldBe "false"

                        respond(
                            content = """{"data":{"signature":"vault:v1:$expectedSignatureB64"}}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    },
                ) {
                    install(ContentNegotiation) {
                        json(json)
                    }
                }

            val provider =
                HashicorpVaultSignatureProvider(
                    client = client,
                    cfg =
                        VaultConfig(
                            url = "http://vault.test/v1/transit",
                            key = "test-key",
                            tokenPath = tokenFile.toString(),
                        ),
                )

            provider.fetchSignature("data".encodeToByteArray()).shouldBeRight() shouldBe expectedSignature
        }
    })

private suspend fun readBodyText(body: OutgoingContent): String =
    when (body) {
        is OutgoingContent.ByteArrayContent -> {
            body.bytes().decodeToString()
        }

        is OutgoingContent.WriteChannelContent -> {
            val channel = ByteChannel()
            body.writeTo(channel)
            channel.readRemaining().readText()
        }

        is OutgoingContent.ReadChannelContent -> {
            body.readFrom().readRemaining().readText()
        }

        else -> {
            body.toString()
        }
    }
