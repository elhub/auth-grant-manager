package no.elhub.auth.config

import com.github.mustachejava.DefaultMustacheFactory
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
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.json.Json
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.ExposedDocumentRepository
import no.elhub.auth.features.documents.confirm.ConfirmDocumentHandler
import no.elhub.auth.features.documents.create.CreateDocumentHandler
import no.elhub.auth.features.documents.create.HashicorpVaultSignatureProvider
import no.elhub.auth.features.documents.create.HtmlToPdfFactory
import no.elhub.auth.features.documents.create.PadESSigningService
import no.elhub.auth.features.documents.create.PdfFactory
import no.elhub.auth.features.documents.create.PdfSigningService
import no.elhub.auth.features.documents.create.SignatureProvider
import no.elhub.auth.features.documents.create.SigningCertificate
import no.elhub.auth.features.documents.create.SigningCertificateChain
import no.elhub.auth.features.documents.get.GetDocumentHandler
import no.elhub.auth.features.grants.common.ExposedGrantRepository
import no.elhub.auth.features.grants.common.GrantRepository
import no.elhub.auth.features.grants.get.GetGrantHandler
import no.elhub.auth.features.grants.getScopes.GetGrantScopesHandler
import no.elhub.auth.features.grants.query.QueryGrantsHandler
import no.elhub.auth.features.requests.common.ExposedRequestRepository
import no.elhub.auth.features.requests.common.RequestRepository
import no.elhub.auth.features.requests.confirm.ConfirmRequestHandler
import no.elhub.auth.features.requests.create.CreateRequestHandler
import no.elhub.auth.features.requests.get.GetRequestHandler
import no.elhub.auth.features.requests.query.QueryRequestsHandler
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

data class VaultConfig(
    val url: String,
    val key: String,
    val tokenPath: String
)

data class CertificateConfig(
    val file: String
)

data class PdfGeneratorConfig(
    val mustacheResourcePath: String
)

val signerModule = module {
    single {
        val cfg = get<ApplicationConfig>().config("pdfSigner.vault")
        VaultConfig(
            url = cfg.property("url").getString(),
            key = cfg.property("key").getString(),
            tokenPath = cfg.property("tokenPath").getString(),
        )
    }

    single<SigningCertificate> {
        val cfg = get<ApplicationConfig>().config("pdfSigner.certificate")
        val path = cfg.property("signing").getString()
        loadCerts(File(path)).single()
    }

    single<SigningCertificateChain> {
        val cfg = get<ApplicationConfig>().config("pdfSigner.certificate")
        val path = cfg.property("chain").getString()
        loadCerts(File(path))
    }
}

val appModule =
    module {
        singleOf(::ExposedDocumentRepository) bind DocumentRepository::class
        singleOf(::ExposedGrantRepository) bind GrantRepository::class
        singleOf(::ExposedRequestRepository) bind RequestRepository::class
        single { PAdESService(CommonCertificateVerifier()) }
        singleOf(::PadESSigningService) bind PdfSigningService::class
        singleOf(::HashicorpVaultSignatureProvider) bind SignatureProvider::class
        singleOf(::HtmlToPdfFactory) bind PdfFactory::class
        single {
            val cfg = get<ApplicationConfig>().config("pdfGenerator")
            val pdfGeneratorCfg = PdfGeneratorConfig(
                mustacheResourcePath = cfg.property("mustacheResourcePath").getString(),
            )

            DefaultMustacheFactory(pdfGeneratorCfg.mustacheResourcePath)
        }
        // TODO: Create dedicated testing module?
        singleOf(::ConfirmDocumentHandler)
        singleOf(::CreateDocumentHandler)
        singleOf(::GetDocumentHandler)

        singleOf(::GetGrantHandler)
        singleOf(::GetGrantScopesHandler)
        singleOf(::QueryGrantsHandler)

        singleOf(::ConfirmRequestHandler)
        singleOf(::CreateRequestHandler)
        singleOf(::GetRequestHandler)
        singleOf(::QueryRequestsHandler)
    }

val httpClientModule = module {
    single {
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
}

fun Application.configureKoin() {
    install(Koin) {
        modules(
            module { single { environment.config } },
            signerModule,
            httpClientModule,
            appModule
        )
    }
}

fun loadCerts(file: File): List<X509Certificate> =
    file.inputStream().use {
        CertificateFactory.getInstance("X.509")
            .generateCertificates(it)
            .filterIsInstance<X509Certificate>()
    }
