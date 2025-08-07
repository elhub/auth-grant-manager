package no.elhub.auth.domain.document

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.*
import kotlinx.serialization.Serializable
import no.elhub.auth.presentation.config.VaultConfig
import no.elhub.auth.presentation.model.PostAuthorizationDocumentRequest

typealias PdfBytes = ByteArray

data class AuthorizationDocument(
    val id: UUID,
    val title: String,
    val type: DocumentType,
    val status: AuthorizationDocumentStatus,
    val pdfBytes: PdfBytes,
    val requestedBy: String,
    val requestedTo: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {

    companion object {
        fun of(createDocumentCommand: CreateAuthorizationDocumentCommand, pdf: PdfBytes): AuthorizationDocument =
            AuthorizationDocument(
                id = UUID.randomUUID(),
                title = "Title",
                pdfBytes = pdf,
                type = DocumentType.ChangeOfSupplierConfirmation,
                status = AuthorizationDocumentStatus.Pending,
                requestedBy = createDocumentCommand.requestedBy,
                requestedTo = createDocumentCommand.requestedTo,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
    }

    enum class AuthorizationDocumentStatus {
        Expired,
        Pending,
        Rejected,
        Signed
    }

    enum class DocumentType {
        ChangeOfSupplierConfirmation
    }
}

data class CreateAuthorizationDocumentCommand(
    val type: AuthorizationDocument.DocumentType,
    val requestedBy: String,
    val requestedTo: String,
    val meteringPoint: String
)

class VaultSignatureProvider(
    private val client: HttpClient,
    private val cfg: VaultConfig,
) {

    @Serializable
    private data class SignRequest(
        val input: String
    )

    @Serializable
    private data class SignResponse(val data: Data) {
        @Serializable
        data class Data(val signature: String)
    }

    private fun readVaultToken(): String {
        val tokenPath = cfg.tokenPath
        return Files.readString(Paths.get(tokenPath)).trim()
    }

    suspend fun sign(digest: ByteArray): ByteArray {
        val b64 = Base64.getEncoder().encodeToString(digest)

        val resp = client.post("${cfg.url}/sign/${cfg.key}") {
            contentType(ContentType.Application.Json)
            header("X-Vault-Token", readVaultToken())
            setBody(SignRequest(b64))
        }.body<SignResponse>()

        val raw = resp.data.signature.removePrefix("vault:v1:")
        return Base64.getDecoder().decode(raw)
    }
}
