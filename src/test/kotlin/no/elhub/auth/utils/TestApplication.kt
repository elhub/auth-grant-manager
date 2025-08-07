package no.elhub.auth.utils

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.TestApplication
import no.elhub.auth.presentation.module

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
