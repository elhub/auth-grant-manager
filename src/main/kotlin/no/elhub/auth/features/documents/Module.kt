package no.elhub.auth.features.documents

import com.oracle.bmc.ConfigFileReader
import com.oracle.bmc.Region
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider
import com.oracle.bmc.auth.SimplePrivateKeySupplier
import com.oracle.bmc.objectstorage.ObjectStorageClient
import eu.europa.esig.dss.pades.signature.PAdESService
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.storage.FileStorage
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.minio.MinioClient
import kotlinx.serialization.json.Json
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.ExposedDocumentRepository
import no.elhub.auth.features.documents.common.FileStorage
import no.elhub.auth.features.documents.common.S3Config
import no.elhub.auth.features.documents.common.S3ObjectStorage
import no.elhub.auth.features.documents.common.OciObjectStorage
import no.elhub.auth.features.documents.common.OciObjectStorageConfig
import no.elhub.auth.features.documents.create.CertificateProvider
import no.elhub.auth.features.documents.create.FileCertificateProvider
import no.elhub.auth.features.documents.create.FileCertificateProviderConfig
import no.elhub.auth.features.documents.create.FileGenerator
import no.elhub.auth.features.documents.create.FileSigningService
import no.elhub.auth.features.documents.create.HashicorpVaultSignatureProvider
import no.elhub.auth.features.documents.create.PdfGenerator
import no.elhub.auth.features.documents.create.PdfGeneratorConfig
import no.elhub.auth.features.documents.create.PdfSigningService
import no.elhub.auth.features.documents.create.SignatureProvider
import no.elhub.auth.features.documents.create.VaultConfig
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.koinModule
import no.elhub.auth.features.documents.confirm.Handler as ConfirmHandler
import no.elhub.auth.features.documents.confirm.route as confirmRoute
import no.elhub.auth.features.documents.create.Handler as CreateHandler
import no.elhub.auth.features.documents.create.route as createRoute
import no.elhub.auth.features.documents.get.Handler as GetHandler
import no.elhub.auth.features.documents.get.route as getRoute
import no.elhub.auth.features.documents.query.Handler as QueryHandler
import no.elhub.auth.features.documents.query.route as queryRoute

const val DOCUMENTS_PATH = "/authorization-documents"

fun Application.module() {
    koinModule {
        single {
            val pdfCertCfg = environment.config.config("pdfSigner.certificate")
            FileCertificateProviderConfig(
                pathToCertificateChain = pdfCertCfg.property("chain").getString(),
                pathToSigningCertificate = pdfCertCfg.property("signing").getString(),
            )
        }
        singleOf(::FileCertificateProvider) bind CertificateProvider::class
        single { PAdESService(CommonCertificateVerifier()) }
        singleOf(::PdfSigningService) bind FileSigningService::class

        factory {
            HttpClient(CIO) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 10_000
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 10_000
                }
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                        }
                    )
                }
                install(Logging) {
                    level = LogLevel.ALL
                }
            }
        }
        single {
            val pdfVaultCfg = environment.config.config("pdfSigner.vault")
            VaultConfig(
                url = pdfVaultCfg.property("url").getString(),
                key = pdfVaultCfg.property("key").getString(),
                tokenPath = pdfVaultCfg.property("tokenPath").getString(),
            )
        }

        singleOf(::HashicorpVaultSignatureProvider) bind SignatureProvider::class

        single {
            val pdfGenCfg = environment.config.config("pdfGenerator")
            PdfGeneratorConfig(
                mustacheResourcePath = pdfGenCfg.property("mustacheResourcePath").getString(),
            )
        }

        singleOf(::PdfGenerator) bind FileGenerator::class

        val pdfStorageCfg = environment.config.config("documentStorage")
        if (pdfStorageCfg.property("storage").getString() == "oci") {
            single {
                val ociCfg = environment.config.config("ociObjectStorage")
                OciObjectStorageConfig(
                    region = ociCfg.property("region").getString(),
                    namespace = ociCfg.property("namespace").getString(),
                    bucket = ociCfg.property("bucket").getString(),
                    linkExpiryHours = ociCfg.property("linkExpiryHours").getAs<Long>(),
                    fingerprint = ociCfg.property("fingerprint").getString(),
                    tenant = ociCfg.property("tenant").getString(),
                    user = ociCfg.property("user").getString(),
                    privateKeyPath = ociCfg.property("privateKeyPath").getString(),
                )
            }

            single {
                val ociCfg = get<OciObjectStorageConfig>()
                val authDetailsProvider = SimpleAuthenticationDetailsProvider
                    .builder()
                    .fingerprint(ociCfg.fingerprint)
                    .privateKeySupplier(SimplePrivateKeySupplier(ociCfg.privateKeyPath))
                    .region(Region.fromRegionCode(ociCfg.region))
                    .tenantId(ociCfg.tenant)
                    .userId(ociCfg.user)
                    .build()

                ObjectStorageClient
                    .builder()
                    .region(ociCfg.region)
                    .build(authDetailsProvider)
            }

            singleOf(::OciObjectStorage) bind FileStorage::class
        } else {
            single {
                val s3Cfg = environment.config.config("s3")
                S3Config(
                    url = s3Cfg.property("url").getString(),
                    bucket = s3Cfg.property("bucket").getString(),
                    region = s3Cfg.property("region").getString(),
                    username = s3Cfg.property("username").getString(),
                    password = s3Cfg.property("password").getString(),
                    linkExpiryHours = s3Cfg.property("linkExpiryHours").getAs<Int>(),
                )
            }

            single {
                val s3Cfg = get<S3Config>()
                MinioClient
                    .builder()
                    .endpoint(s3Cfg.url)
                    .region(s3Cfg.region)
                    .credentials(s3Cfg.username, s3Cfg.password)
                    .build()
            }

            singleOf(::S3ObjectStorage) bind FileStorage::class
        }

        singleOf(::ExposedDocumentRepository) bind DocumentRepository::class
        singleOf(::ConfirmHandler)
        singleOf(::CreateHandler)
        singleOf(::GetHandler)
        singleOf(::QueryHandler)
    }

    routing {
        route(DOCUMENTS_PATH) {
            createRoute(get())
            confirmRoute(get())
            getRoute(get())
            queryRoute(get())
        }
    }
}
