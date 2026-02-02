package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.right
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Base64

interface SignatureProvider {
    suspend fun fetchSignature(digest: ByteArray): Either<SignatureFetchingError, ByteArray>
}

sealed class SignatureFetchingError {
    data object UnexpectedError : SignatureFetchingError()
}

data class VaultConfig(
    val url: String,
    val key: String,
    val tokenPath: String,
)

class HashicorpVaultSignatureProvider(
    private val client: HttpClient,
    private val cfg: VaultConfig,
) : SignatureProvider {
    private val logger = LoggerFactory.getLogger(HashicorpVaultSignatureProvider::class.java)

    @Serializable
    internal enum class HashAlgorithm {
        @SerialName("sha2-256")
        SHA2_256,
    }

    @Serializable
    internal enum class SignatureAlgorithm {
        @SerialName("pkcs1v15")
        PKCS1V15,
    }

    @Serializable
    private data class SignRequest(
        val input: String,
        val hash_algorithm: HashAlgorithm,
        val signature_algorithm: SignatureAlgorithm,
        val prehashed: Boolean,
    )

    @Serializable
    private data class SignResponse(
        val data: Data,
    ) {
        @Serializable
        data class Data(
            val signature: String,
        )
    }

    private fun readVaultToken() =
        Files
            .readString(
                Paths.get(cfg.tokenPath),
            ).trim()

    override suspend fun fetchSignature(digest: ByteArray): Either<SignatureFetchingError, ByteArray> =
        Either
            .catch {
                val b64 = Base64.getEncoder().encodeToString(digest)

                val resp =
                    client
                        .post("${cfg.url}/sign/${cfg.key}") {
                            contentType(ContentType.Application.Json)
                            header("X-Vault-Token", readVaultToken())
                            setBody(
                                SignRequest(
                                    input = b64,
                                    hash_algorithm = HashAlgorithm.SHA2_256,
                                    signature_algorithm = SignatureAlgorithm.PKCS1V15,
                                    prehashed = false,
                                ),
                            )
                        }.body<SignResponse>()

                val raw = resp.data.signature.removePrefix("vault:v1:")
                return Base64.getDecoder().decode(raw).right()
            }.mapLeft {
                logger.error("Failed to fetch signature from Vault", it)
                SignatureFetchingError.UnexpectedError
            }
}
