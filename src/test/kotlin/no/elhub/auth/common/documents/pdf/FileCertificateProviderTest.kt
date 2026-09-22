package no.elhub.auth.common.documents.pdf

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.elhub.auth.v0.features.documents.TempBankIdCertificatesLocation
import no.elhub.auth.v0.features.documents.TestCertificateUtil
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class FileCertificateProviderTest : FunSpec({
    test("loads configured signing and trust certificates") {
        val bankIdCertificates = TempBankIdCertificatesLocation.create()
        val provider = FileCertificateProvider(
            FileCertificateProviderConfig(
                pathToIntermSigningCertificate = TestCertificateUtil.Constants.INTERMEDIATE_CERTIFICATE_LOCATION,
                pathToSigningCertificate = TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
                pathToBankIdRootCertificatesDir = bankIdCertificates.bankIdRootCertificatesDir,
                pathToTsaRootCertificatesDir = bankIdCertificates.bankIdRootCertificatesDir,
            )
        )

        val expectedSigningCertificate = loadCertificate(TestCertificateUtil.Constants.CERTIFICATE_LOCATION)
        val expectedIntermediateCertificate =
            loadCertificate(TestCertificateUtil.Constants.INTERMEDIATE_CERTIFICATE_LOCATION)

        provider.getElhubSigningCertificate().encoded shouldBe expectedSigningCertificate.encoded
        provider.getElhubIntermediateCertificate().encoded shouldBe expectedIntermediateCertificate.encoded
        provider.getBankIdRootCertificates().isNotEmpty() shouldBe true
        provider.getTsaRootCertificates().isNotEmpty() shouldBe true
    }

    test("rejects a missing trust certificate directory") {
        val missingDirectory = File("build/tmp/missing-pdf-certificates")

        io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
            FileCertificateProvider(
                FileCertificateProviderConfig(
                    pathToIntermSigningCertificate = TestCertificateUtil.Constants.INTERMEDIATE_CERTIFICATE_LOCATION,
                    pathToSigningCertificate = TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
                    pathToBankIdRootCertificatesDir = missingDirectory.path,
                    pathToTsaRootCertificatesDir = missingDirectory.path,
                )
            )
        }
    }
})

private fun loadCertificate(path: String): X509Certificate =
    File(path).inputStream().use { input ->
        CertificateFactory.getInstance("X.509").generateCertificate(input) as X509Certificate
    }
