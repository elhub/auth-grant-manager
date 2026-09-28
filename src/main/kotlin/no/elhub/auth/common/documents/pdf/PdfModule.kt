package no.elhub.auth.common.documents.pdf

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.di.dependencies

fun Application.pdfModule() {
    dependencies {
        provide<FileCertificateProviderConfig> {
            val cfg = resolve<ApplicationConfig>().config("pdfSigner.certificate")
            FileCertificateProviderConfig(
                pathToIntermSigningCertificate = cfg.property("intermediate").getString(),
                pathToSigningCertificate = cfg.property("signing").getString(),
                pathToBankIdRootCertificatesDir = cfg.property("bankIdRootDir").getString(),
                pathToTsaRootCertificatesDir = cfg.property("tsaRootDir").getString(),
            )
        }
        provide<CertificateProvider> { FileCertificateProvider(resolve()) }
        provide<VaultConfig> {
            val cfg = resolve<ApplicationConfig>().config("pdfSigner.vault")
            VaultConfig(
                url = cfg.property("url").getString(),
                key = cfg.property("key").getString(),
                tokenPath = cfg.property("tokenPath").getString(),
            )
        }
        provide<SignatureProvider> {
            HashicorpVaultSignatureProvider(
                client = resolve("commonHttpClient"),
                cfg = resolve()
            )
        }
        provide<PdfGeneratorConfig> {
            val cfg = resolve<ApplicationConfig>().config("pdfGenerator")
            PdfGeneratorConfig(
                mustacheResourcePath = cfg.property("mustacheResourcePath").getString(),
                useTestPdfNotice = cfg.property("useTestPdfNotice").getString().toBoolean(),
            )
        }
        provide<PdfGenerator> { MustachePdfGenerator(resolve()) }
        provide<ITextPdfSignatureService> { ITextPdfSignatureService(resolve(), resolve()) }
        provide<PdfSigner> { resolve<ITextPdfSignatureService>() }
        provide<PdfSignatureValidator> { resolve<ITextPdfSignatureService>() }
    }
}
