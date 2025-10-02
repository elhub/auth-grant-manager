package no.elhub.auth.features.documents.common

import arrow.core.Either
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
    suspend fun upsert(inputStream: InputStream, filename: String): Either<FileStorageError, URI>
    suspend fun find(filename: String): Either<FileStorageError, URI>
}

sealed class FileStorageError {
    data object UnexpectedError : FileStorageError()
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

    override suspend fun upsert(inputStream: InputStream, filename: String): Either<FileStorageError, URI> =
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

            client.putObject(
                PutObjectArgs
                    .builder()
                    .bucket(cfg.bucket)
                    .`object`(filename)
                    .stream(inputStream, -1, 10485760)
                    .contentType(MIME_TYPE_PDF)
                    .build()
            )

            return find(filename)
        }.mapLeft { FileStorageError.UnexpectedError }

    override suspend fun find(filename: String): Either<FileStorageError, URI> =
        Either.catch {
            URI(
                client.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs
                        .builder()
                        .method(Method.GET)
                        .bucket(cfg.bucket)
                        .`object`(filename)
                        .expiry(cfg.linkExpiryHours, TimeUnit.HOURS)
                        .extraQueryParams(mapOf(KEY_CONTENT_TYPE to MIME_TYPE_PDF))
                        .build()
                )
            )
        }.mapLeft { FileStorageError.UnexpectedError }
}
