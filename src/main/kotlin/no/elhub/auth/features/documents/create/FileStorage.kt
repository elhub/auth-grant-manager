package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.minio.MinioClient
import io.minio.PutObjectArgs
import kotlinx.serialization.Serializable
import org.apache.commons.lang3.NotImplementedException
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths

interface FileStorage {
    suspend fun upload(inputStream: InputStream, filename: String): Either<FileUploadError, URI>
}

sealed class FileUploadError {
    data object UnexpectedError : FileUploadError()
}

private const val MIME_TYPE_PDF = "application/pdf"

data class MinioConfig(
    val url: String,
    val externalUrl: String,
    val bucket: String,
)

class MinioFileStorage(
    private val cfg: MinioConfig,
    private val client: MinioClient,
) : FileStorage {

    override suspend fun upload(inputStream: InputStream, filename: String): Either<FileUploadError, URI> =
        Either.catch {
            val response =
                client.putObject(
                    PutObjectArgs
                        .builder()
                        .bucket(cfg.bucket)
                        .`object`(filename)
                        .stream(inputStream, -1, 10485760)
                        .contentType(MIME_TYPE_PDF)
                        .build()
                )
            URI("${cfg.url}/${cfg.bucket}/$filename")
        }.mapLeft { FileUploadError.UnexpectedError }
}
