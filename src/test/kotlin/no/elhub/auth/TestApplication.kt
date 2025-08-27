package no.elhub.auth

import eu.europa.esig.dss.pades.signature.PAdESService
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import io.ktor.server.application.Application
import io.ktor.server.application.install
import no.elhub.auth.features.grants.common.ExposedGrantRepository
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.TestApplication
import no.elhub.auth.config.httpClientModule
import no.elhub.auth.features.documents.TestCertificateUtil
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.common.ExposedDocumentRepository
import no.elhub.auth.features.documents.confirm.ConfirmDocumentHandler
import no.elhub.auth.features.documents.create.CreateDocumentHandler
import no.elhub.auth.features.documents.create.DocumentGenerator
import no.elhub.auth.features.documents.create.DssSigningService
import no.elhub.auth.features.documents.create.PdfDocumentGenerator
import no.elhub.auth.features.documents.create.SigningService
import no.elhub.auth.features.documents.create.VaultSignatureProvider
import no.elhub.auth.features.documents.get.GetDocumentHandler
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
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.dsl.bind
import org.koin.ktor.plugin.Koin
import no.elhub.auth.config.signerModule

fun defaultTestApplication(): TestApplication = TestApplication {
    application {
        module()
    }

    environment {
        config = MapApplicationConfig(
            "ktor.database.username" to "app",
            "ktor.database.password" to "app",
            "ktor.database.url" to "jdbc:postgresql://localhost:5432/auth",
            "ktor.database.driverClass" to "org.postgresql.Driver",
            "pdfSigner.vault.url" to "http://localhost:8200/v1/transit",
            "pdfSigner.vault.tokenPath" to "src/test/resources/vault_token_mock.txt",
            "pdfSigner.vault.key" to "test-key",
            "pdfSigner.certificate.signing" to TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
            "pdfSigner.certificate.chain" to TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
        )
    }
}
