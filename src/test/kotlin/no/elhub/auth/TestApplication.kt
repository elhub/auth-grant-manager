package no.elhub.auth

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.TestApplication
import no.elhub.auth.features.documents.TestCertificateUtil
import no.elhub.auth.features.documents.module as documentsModule
import no.elhub.auth.features.grants.module as grantsModule
import no.elhub.auth.features.openapi.module as openapiModule
import no.elhub.auth.features.requests.module as requestsModule

fun defaultTestApplication(): TestApplication = TestApplication {
    application {
        module()
        documentsModule()
        grantsModule()
        openapiModule()
        requestsModule()
    }

    environment {
        config = MapApplicationConfig(
            "ktor.database.username" to "app",
            "ktor.database.password" to "app",
            "ktor.database.url" to "jdbc:postgresql://localhost:5432/auth",
            "ktor.database.driverClass" to "org.postgresql.Driver",
            "pdfGenerator.mustacheResourcePath" to "templates",
            "pdfSigner.vault.url" to "http://localhost:8200/v1/transit",
            "pdfSigner.vault.tokenPath" to "src/test/resources/vault_token_mock.txt",
            "pdfSigner.vault.key" to "test-key",
            "pdfSigner.certificate.signing" to TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
            "pdfSigner.certificate.chain" to TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
        )
    }
}
