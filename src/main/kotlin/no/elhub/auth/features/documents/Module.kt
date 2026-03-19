package no.elhub.auth.features.documents

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.documents.common.DocumentPropertiesRepository
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.ExposedDocumentPropertiesRepository
import no.elhub.auth.features.documents.common.ExposedDocumentRepository
import no.elhub.auth.features.documents.common.ITextPdfSignatureService
import no.elhub.auth.features.documents.create.FileCertificateProvider
import no.elhub.auth.features.documents.create.FileCertificateProviderConfig
import no.elhub.auth.features.documents.create.FileGenerator
import no.elhub.auth.features.documents.create.HashicorpVaultSignatureProvider
import no.elhub.auth.features.documents.create.VaultConfig
import no.elhub.auth.features.filegenerator.PdfGenerator
import no.elhub.auth.features.filegenerator.PdfGeneratorConfig
import no.elhub.auth.features.documents.confirm.Handler as ConfirmHandler
import no.elhub.auth.features.documents.confirm.route as confirmRoute
import no.elhub.auth.features.documents.create.Handler as CreateHandler
import no.elhub.auth.features.documents.create.route as createRoute
import no.elhub.auth.features.documents.get.Handler as GetHandler
import no.elhub.auth.features.documents.get.route as getRoute
import no.elhub.auth.features.documents.query.Handler as QueryHandler
import no.elhub.auth.features.documents.query.route as queryRoute

const val DOCUMENTS_PATH = "/access/v0/authorization-documents"

fun Application.module() {
    dependencies {
        provide<FileCertificateProvider> {
            FileCertificateProvider(resolve())
        }

        provide<FileCertificateProviderConfig> {
            val cfg = resolve<ApplicationConfig>().config("pdfSigner.certificate")
            FileCertificateProviderConfig(
                pathToIntermSigningCertificate = cfg.property("intermediate").getString(),
                pathToSigningCertificate = cfg.property("signing").getString(),
                pathToBankIdRootCertificatesDir = cfg.property("bankIdRootDir").getString(),
                pathToTsaRootCertificatesDir = cfg.property("tsaRootDir").getString(),
            )
        }

        provide<ITextPdfSignatureService> {
            ITextPdfSignatureService(resolve(), resolve())
        }
        provide<VaultConfig> {
            val cfg = resolve<ApplicationConfig>().config("pdfSigner.vault")
            VaultConfig(
                url = cfg.property("url").getString(),
                key = cfg.property("key").getString(),
                tokenPath = cfg.property("tokenPath").getString(),
            )
        }

        provide<HashicorpVaultSignatureProvider> {
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
        provide<FileGenerator> { PdfGenerator(resolve()) }
        provide<DocumentRepository> { ExposedDocumentRepository(resolve(), resolve()) }
        provide<DocumentPropertiesRepository> { ExposedDocumentPropertiesRepository() }
        provide<ConfirmHandler> { ConfirmHandler(resolve(), resolve(), resolve(), resolve(), resolve(), resolve()) }
        provide<CreateHandler> { CreateHandler(resolve(), resolve(), resolve(), resolve(), resolve()) }
        provide<GetHandler> { GetHandler(resolve(), resolve()) }
        provide<QueryHandler> { QueryHandler(resolve(), resolve()) }
    }

    val createHandler: CreateHandler by dependencies
    val confirmHandler: ConfirmHandler by dependencies
    val getHandler: GetHandler by dependencies
    val queryHandler: QueryHandler by dependencies
    val authProvider: AuthorizationProvider by dependencies

    routing {
        route(DOCUMENTS_PATH) {
            createRoute(createHandler, authProvider)
            confirmRoute(confirmHandler, authProvider)
            getRoute(getHandler, authProvider)
            queryRoute(queryHandler, authProvider)
        }
    }
}
