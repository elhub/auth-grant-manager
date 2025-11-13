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
import no.elhub.auth.features.common.ExposedPartyRepository
import no.elhub.auth.features.common.PartyRepository
import no.elhub.auth.features.common.shouldRegisterEndpoint
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.ExposedDocumentRepository
import no.elhub.auth.features.documents.create.ApiPersonService
import no.elhub.auth.features.documents.create.CertificateProvider
import no.elhub.auth.features.documents.create.FileCertificateProvider
import no.elhub.auth.features.documents.create.FileCertificateProviderConfig
import no.elhub.auth.features.documents.create.FileGenerator
import no.elhub.auth.features.documents.create.FileSigningService
import no.elhub.auth.features.documents.create.HashicorpVaultSignatureProvider
import no.elhub.auth.features.documents.create.PdfGenerator
import no.elhub.auth.features.documents.create.PdfGeneratorConfig
import no.elhub.auth.features.documents.create.PdfSigningService
import no.elhub.auth.features.documents.create.PersonApiConfig
import no.elhub.auth.features.documents.create.PersonService
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

        single {
            val cfg = get<ApplicationConfig>().config("authPersons")
            PersonApiConfig(
                baseUri = cfg.property("baseUri").getString()
            )
        }

        singleOf(::PdfGenerator) bind FileGenerator::class
        singleOf(::ExposedDocumentRepository) bind DocumentRepository::class
        singleOf(::ExposedPartyRepository) bind PartyRepository::class
        singleOf(::ApiPersonService) bind PersonService::class
        singleOf(::ConfirmHandler)
        singleOf(::CreateHandler)
        singleOf(::GetHandler)
        singleOf(::QueryHandler)
    }

    routing {
        route(DOCUMENTS_PATH) {
            shouldRegisterEndpoint {
                createRoute(get())
                confirmRoute(get())
                getRoute(get())
                queryRoute(get())
            }
        }
    }
}
