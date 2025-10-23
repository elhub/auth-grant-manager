package no.elhub.auth.features.documents.common

import arrow.core.Either
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest
import com.oracle.bmc.objectstorage.requests.PutObjectRequest
import java.io.InputStream
import java.net.URI
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

interface FileStorage {
    suspend fun upsert(inputStream: InputStream, filename: String): Either<FileStorageError, URI>
    suspend fun find(filename: String): Either<FileStorageError, URI>
}

sealed class FileStorageError {
    data object UnexpectedError : FileStorageError()
}

private const val KEY_CONTENT_TYPE = "response-content-type"
private const val MIME_TYPE_PDF = "application/pdf"

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
