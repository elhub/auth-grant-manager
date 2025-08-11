package no.elhub.auth.grantmanager.presentation.config

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import no.elhub.auth.grantmanager.data.config.HashicorpVaultConfig
import no.elhub.auth.grantmanager.domain.repositories.GrantRepository
import no.elhub.auth.grantmanager.domain.usecases.changeSupplier.getRequest.GetRequestUseCase
import no.elhub.auth.grantmanager.data.repositories.ExposedGrantRepository
import no.elhub.auth.grantmanager.data.services.HashicorpVaultSigningService
import no.elhub.auth.grantmanager.presentation.features.documents.AuthorizationDocumentHandler
import no.elhub.auth.grantmanager.presentation.features.documents.DocumentSigningService
import no.elhub.auth.grantmanager.data.services.SigningCertificate
import no.elhub.auth.grantmanager.data.services.SigningCertificateChain
import no.elhub.auth.grantmanager.presentation.features.grants.AuthorizationGrantHandler
import no.elhub.auth.grantmanager.presentation.features.requests.AuthorizationRequestHandler
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

data class CertificateConfig(
    val file: String
)

val signerModule = module {
    single {
        val cfg = get<ApplicationConfig>().config("pdfSigner.vault")
        HashicorpVaultConfig(
            url = cfg.property("url").getString(),
            key = cfg.property("key").getString(),
            token = cfg.property("token").getString(),
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
        singleOf(::HashicorpVaultSigningService)
        singleOf(::DocumentSigningService)
        singleOf(::AuthorizationGrantHandler)
        singleOf(::AuthorizationDocumentHandler)
        singleOf(::AuthorizationRequestHandler)
        singleOf(::ExposedGrantRepository) bind GrantRepository::class
        singleOf(::GetRequestUseCase)
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
                json()
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
