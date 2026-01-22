package no.elhub.auth.features.documents

import eu.europa.esig.dss.enumerations.SignatureLevel
import java.time.Duration
import java.time.Instant

// Test helper to mock external/end-user signatures during the document flow.
class EndUserSignatureTestHelper(
    private val signatureLevel: SignatureLevel = SignatureLevel.PAdES_BASELINE_B,
    defaultValidity: Duration = Duration.ofMinutes(30),
    private val certFactory: TestCertificateFactory = TestCertificateFactory(defaultValidity)
) {
    private val rootCertificate = certFactory.trustedBankIdRootCertificate
    private val rootKeyPair = certFactory.trustedBankIdRootKeyPair

    fun sign(
        pdfBytes: ByteArray,
        nationalIdentityNumber: String,
        notBefore: Instant? = null,
        notAfter: Instant? = null
    ): ByteArray {
        val keyPair = certFactory.generateKeyPair()
        val certificate = certFactory.generateLeafCertificateWithNationalId(
            keyPair = keyPair,
            issuerKeyPair = rootKeyPair,
            issuerCertificate = rootCertificate,
            nationalIdentityNumber = nationalIdentityNumber,
            notBeforeOverride = notBefore,
            notAfterOverride = notAfter
        )
        return TestPdfSigner.signWithCertificate(
            pdfBytes = pdfBytes,
            signingCert = certificate,
            chain = listOf(certificate, rootCertificate),
            signingKey = keyPair.private,
            signatureLevel = signatureLevel
        )
    }

    fun signWithUntrustedCertificate(
        pdfBytes: ByteArray,
        nationalIdentityNumber: String
    ): ByteArray {
        val rootKeyPair = certFactory.generateKeyPair()
        val selfSignedRoot = certFactory.generateSelfSignedRootCertificate(
            keyPair = rootKeyPair,
            templateCertificate = rootCertificate
        )
        val leafKeyPair = certFactory.generateKeyPair()
        val certificate = certFactory.generateLeafCertificateWithNationalId(
            keyPair = leafKeyPair,
            issuerKeyPair = rootKeyPair,
            issuerCertificate = selfSignedRoot,
            nationalIdentityNumber = nationalIdentityNumber,
            notBeforeOverride = null,
            notAfterOverride = null
        )
        return TestPdfSigner.signWithCertificate(
            pdfBytes = pdfBytes,
            signingCert = certificate,
            chain = listOf(certificate, selfSignedRoot),
            signingKey = leafKeyPair.private,
            signatureLevel = signatureLevel
        )
    }

    fun signWithoutNationalIdExtension(
        pdfBytes: ByteArray
    ): ByteArray {
        val keyPair = certFactory.generateKeyPair()
        val certificate = certFactory.generateLeafCertificateWithoutNationalId(
            keyPair = keyPair,
            issuerKeyPair = rootKeyPair,
            issuerCertificate = rootCertificate
        )
        return TestPdfSigner.signWithCertificate(
            pdfBytes = pdfBytes,
            signingCert = certificate,
            chain = listOf(certificate, rootCertificate),
            signingKey = keyPair.private,
            signatureLevel = signatureLevel
        )
    }
}
