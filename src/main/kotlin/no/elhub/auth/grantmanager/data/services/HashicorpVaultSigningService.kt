package no.elhub.auth.grantmanager.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import no.elhub.auth.grantmanager.data.config.HashicorpVaultConfig
import no.elhub.auth.grantmanager.domain.services.ExternalSigningService
import java.util.Base64

class HashicorpVaultSigningService(
    private val client: HttpClient,
    private val cfg: HashicorpVaultConfig,
) : ExternalSigningService {
    override suspend fun sign(digest: ByteArray): ByteArray {
        val b64 = Base64.getEncoder().encodeToString(digest)

        val resp = client.post("${cfg.url}/v1/transit/sign/${cfg.key}") {
            contentType(ContentType.Application.Json)
            header("X-Vault-Token", cfg.token)
            setBody(SignRequest(b64))
        }.body<SignResponse>()

        val raw = resp.data.signature.removePrefix("vault:v1:")
        return Base64.getDecoder().decode(raw)
    }
}

@Serializable
private data class SignRequest(val input: String)

@Serializable private data class SignResponse(val data: Data) {
    @Serializable data class Data(val signature: String)
}
