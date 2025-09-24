package no.elhub.auth.features.documents

import eu.europa.esig.dss.pades.signature.PAdESService
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.ExposedDocumentRepository
import no.elhub.auth.features.documents.confirm.ConfirmDocumentHandler
import no.elhub.auth.features.documents.confirm.confirmDocumentRoute
import no.elhub.auth.features.documents.create.CertificateProvider
import no.elhub.auth.features.documents.create.CreateDocumentHandler
import no.elhub.auth.features.documents.create.FileCertificateProvider
import no.elhub.auth.features.documents.create.FileCertificateProviderConfig
import no.elhub.auth.features.documents.create.FileGenerator
import no.elhub.auth.features.documents.create.FileSigningService
import no.elhub.auth.features.documents.create.HashicorpVaultSignatureProvider
import no.elhub.auth.features.documents.create.PAdESDocumentSigningService
import no.elhub.auth.features.documents.create.PdfGenerator
import no.elhub.auth.features.documents.create.PdfGeneratorConfig
import no.elhub.auth.features.documents.create.SignatureProvider
import no.elhub.auth.features.documents.create.VaultConfig
import no.elhub.auth.features.documents.create.createDocumentRoute
import no.elhub.auth.features.documents.get.GetDocumentHandler
import no.elhub.auth.features.documents.get.getDocumentRoute
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.koinModule

const val DOCUMENTS_PATH = "/authorization-documents"

fun Application.module() {
    koinModule {
        single { environment.config }
        single {
            val cfg = get<ApplicationConfig>().config("pdfSigner.certificate")
            FileCertificateProviderConfig(
                pathToCertificateChain = cfg.property("chain").getString(),
                pathToSigningCertificate = cfg.property("signing").getString(),
            )
        }
        singleOf(::FileCertificateProvider) bind CertificateProvider::class
        single { PAdESService(CommonCertificateVerifier()) }
        singleOf(::PAdESDocumentSigningService) bind FileSigningService::class

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
            val cfg = get<ApplicationConfig>().config("pdfSigner.vault")
            VaultConfig(
                url = cfg.property("url").getString(),
                key = cfg.property("key").getString(),
                tokenPath = cfg.property("tokenPath").getString(),
            )
        }

        singleOf(::HashicorpVaultSignatureProvider) bind SignatureProvider::class

        single {
            val cfg = get<ApplicationConfig>().config("pdfGenerator")
            PdfGeneratorConfig(
                mustacheResourcePath = cfg.property("mustacheResourcePath").getString(),
            )
        }
        singleOf(::PdfGenerator) bind FileGenerator::class
        singleOf(::ExposedDocumentRepository) bind DocumentRepository::class
        singleOf(::ConfirmDocumentHandler)
        singleOf(::CreateDocumentHandler)
        singleOf(::GetDocumentHandler)
    }

    routing {
        route(DOCUMENTS_PATH) {
            createDocumentRoute(get(), get())
            confirmDocumentRoute(get())
            getDocumentRoute(get())
        }
    }
}
