package no.elhub.auth.features.documents.create

import arrow.core.Either
import io.ktor.http.contentType
import io.minio.BucketExistsArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.http.Method
import java.io.InputStream
import java.net.URI
import java.util.concurrent.TimeUnit

interface FileStorage {
    suspend fun upload(inputStream: InputStream, filename: String): Either<FileUploadError, URI>
}

sealed class FileUploadError {
    data object UnexpectedError : FileUploadError()
}

private const val KEY_CONTENT_TYPE = "response-content-type"
private const val MIME_TYPE_PDF = "application/pdf"

data class MinioConfig(
    val url: String,
    val username: String,
    val password: String,
    val bucket: String,
    val linkExpiryHours: Int,
)

class MinioFileStorage(
    private val cfg: MinioConfig,
    private val client: MinioClient,
) : FileStorage {

    override suspend fun upload(inputStream: InputStream, filename: String): Either<FileUploadError, URI> =
        Either.catch {
            val bucketExists = client.bucketExists(
                BucketExistsArgs
                    .builder()
                    .bucket(cfg.bucket)
                    .build()
            )

            if (!bucketExists) {
                client.makeBucket(
                    MakeBucketArgs
                        .builder()
                        .bucket(cfg.bucket)
                        .build()
                )
            }

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

            val shareableLink = client.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs
                    .builder()
                    .method(Method.GET)
                    .bucket(cfg.bucket)
                    .`object`(filename)
                    .expiry(cfg.linkExpiryHours, TimeUnit.HOURS)
                    .extraQueryParams(mapOf(KEY_CONTENT_TYPE to MIME_TYPE_PDF))
                    .build()
            )

            URI(shareableLink)
        }.mapLeft { error ->
            println(error.message)
            FileUploadError.UnexpectedError
        }
}
