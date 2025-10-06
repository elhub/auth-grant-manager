package no.elhub.auth.features.documents.common

import arrow.core.Either
import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.model.CreateBucketDetails
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest
import com.oracle.bmc.objectstorage.requests.GetBucketRequest
import com.oracle.bmc.objectstorage.requests.PutObjectRequest
import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.ServerSideEncryption
import io.minio.ServerSideEncryptionKms
import io.minio.http.Method
import java.io.InputStream
import java.net.URI
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream

interface FileStorage {
    suspend fun upsert(inputStream: InputStream, filename: String): Either<FileStorageError, URI>
    suspend fun find(filename: String): Either<FileStorageError, URI>
    suspend fun getThing(filename: String): Either<FileStorageError, InputStream>
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

            val sseKms = ServerSideEncryptionKms("", mapOf("" to ""))

            client.putObject(
                PutObjectArgs
                    .builder()
                    .bucket(cfg.bucket)
                    .`object`(filename)
                    .stream(inputStream, -1, 10485760)
                    .contentType(MIME_TYPE_PDF)
                    .sse(sseKms)
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

    override suspend fun getThing(filename: String): Either<FileStorageError, InputStream> =
        Either.catch {
            client.getObject(
                GetObjectArgs
                    .builder()
                    .bucket(cfg.bucket)
                    .`object`(filename)
                    .extraQueryParams(
                        mapOf(KEY_CONTENT_TYPE to MIME_TYPE_PDF)
                    )
                    .build()
            )
        }.mapLeft { FileStorageError.UnexpectedError }
}

data class OciObjectStorageConfig(
    val url: String,
    val bucket: String,
    val linkExpiryHours: Long,
)

class OciObjectStorage(
    val cfg: OciObjectStorageConfig,
    val ociClient: ObjectStorage,
) : FileStorage {

    override suspend fun upsert(inputStream: InputStream, filename: String): Either<FileStorageError, URI> =
        Either.catch {
            val bucketExists = ociClient.getBucket(
                GetBucketRequest
                    .builder()
                    .bucketName(cfg.bucket)
                    .build()
            ) != null

            if (!bucketExists) {
                ociClient.createBucket(
                    CreateBucketRequest
                        .builder()
                        .createBucketDetails(
                            CreateBucketDetails
                                .builder()
                                .name(cfg.bucket)
                                .build()
                        )
                        .build()
                )
            }

            ociClient.putObject(
                PutObjectRequest
                    .builder()
                    .bucketName(cfg.bucket)
                    .objectName(filename)
                    .putObjectBody(inputStream)
                    .contentType(MIME_TYPE_PDF)
                    .build()
            )

            return find(filename)
        }.mapLeft { FileStorageError.UnexpectedError }

    override suspend fun find(filename: String): Either<FileStorageError, URI> = Either.catch {
        URI(
            ociClient.createPreauthenticatedRequest(
                CreatePreauthenticatedRequestRequest
                    .builder()
                    .createPreauthenticatedRequestDetails(
                        CreatePreauthenticatedRequestDetails
                            .builder()
                            .objectName(filename)
                            .timeExpires(
                                Date.from(
                                    Instant
                                        .now()
                                        .plus(cfg.linkExpiryHours, ChronoUnit.HOURS)
                                )
                            )
                            .build()
                    )
                    .build()
            )
                .preauthenticatedRequest
                .accessUri
        )
    }.mapLeft { FileStorageError.UnexpectedError }
}
