package no.elhub.auth.providers.vault

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import no.elhub.auth.config.VaultConfig
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Base64

class VaultSignatureProvider(
    private val client: HttpClient,
    private val cfg: VaultConfig,
) {

    @Serializable
    private data class SignRequest(
        val input: String,
        val prehashed: Boolean = true
    )

    @Serializable private data class SignResponse(val data: Data) {
        @Serializable data class Data(val signature: String)
    }

    private fun readVaultToken(): String {
        val tokenPath = cfg.tokenPath
        return Files.readString(Paths.get(tokenPath)).trim()
    }

    suspend fun sign(digest: ByteArray): ByteArray {
        val b64 = Base64.getEncoder().encodeToString(digest)

        val resp = client.post("${cfg.url}/v1/transit/sign/${cfg.key}") {
            contentType(ContentType.Application.Json)
            header("X-Vault-Token", readVaultToken())
            setBody(SignRequest(b64))
        }.body<SignResponse>()

        val raw = resp.data.signature.removePrefix("vault:v1:")
        return Base64.getDecoder().decode(raw)
    }
}
