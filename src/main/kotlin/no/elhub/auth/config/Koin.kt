package no.elhub.auth.config

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
import no.elhub.auth.features.documents.AuthorizationDocumentHandler
import no.elhub.auth.features.documents.SigningService
import no.elhub.auth.features.grants.AuthorizationGrantHandler
import no.elhub.auth.features.requests.AuthorizationRequestHandler
import no.elhub.auth.providers.vault.VaultSignatureProvider
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

typealias SigningCertificate = X509Certificate
typealias SigningCertificateChain = List<X509Certificate>

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
        single { VaultSignatureProvider(get(), get()) }
        single { SigningService(get(), get(), get()) }
        single { AuthorizationGrantHandler() }
        single { AuthorizationDocumentHandler(get()) }
        single { AuthorizationRequestHandler() }
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
