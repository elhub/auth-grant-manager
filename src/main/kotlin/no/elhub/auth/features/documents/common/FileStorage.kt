package no.elhub.auth.features.documents.common

import arrow.core.Either
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest
import com.oracle.bmc.objectstorage.requests.PutObjectRequest
import io.minio.BucketExistsArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.http.Method
import java.io.InputStream
import java.net.URI
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
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

data class S3Config(
    val url: String,
    val username: String,
    val password: String,
    val region: String,
    val bucket: String,
    val linkExpiryHours: Int,
)

data class S3ObjectStorage(
    val cfg: S3Config,
    val client: MinioClient,
) : FileStorage {
    override suspend fun upsert(inputStream: InputStream, filename: String): Either<FileStorageError, URI> =
        Either.catch {
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

data class OciObjectStorageConfig(
    val region: String,
    val bucket: String,
    val namespace: String,
    val linkExpiryHours: Long,
    val fingerprint: String,
    val tenant: String,
    val user: String,
    val privateKeyPath: String,
)

data class OciObjectStorage(
    val cfg: OciObjectStorageConfig,
    val ociClient: ObjectStorageClient,
) : FileStorage {

    override suspend fun upsert(inputStream: InputStream, filename: String): Either<FileStorageError, URI> =
        Either.catch {
            ociClient.putObject(
                PutObjectRequest
                    .builder()
                    .namespaceName(cfg.namespace)
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
                    .namespaceName(cfg.namespace)
                    .bucketName(cfg.bucket)
                    .createPreauthenticatedRequestDetails(
                        CreatePreauthenticatedRequestDetails
                            .builder()
                            .name("Read $filename")
                            .accessType(CreatePreauthenticatedRequestDetails.AccessType.ObjectRead)
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
