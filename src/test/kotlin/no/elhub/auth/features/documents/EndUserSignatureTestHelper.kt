package no.elhub.auth.features.documents

import java.security.PrivateKey
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant

// Test helper to mock external/end-user signatures during the document flow.
class EndUserSignatureTestHelper(
    defaultValidity: Duration = Duration.ofMinutes(30),
    private val certFactory: TestCertificateFactory = TestCertificateFactory(defaultValidity)
) {
    private val rootCertificate = certFactory.trustedBankIdRootCertificate
    private val rootKeyPair = certFactory.trustedBankIdRootKeyPair
    private val tsaKeyPair = certFactory.generateKeyPair()
    private val tsaCertificate = certFactory.generateTsaCertificate(
        keyPair = tsaKeyPair,
        issuerKeyPair = rootKeyPair,
        issuerCertificate = rootCertificate
    )
    private val tsaConfig = TsaConfig(
        privateKey = tsaKeyPair.private,
        certificate = tsaCertificate,
        chain = listOf(tsaCertificate, rootCertificate)
    )

    private fun crlFor(
        issuerCertificate: X509Certificate,
        issuerKey: PrivateKey,
        revokedSerials: List<java.math.BigInteger> = emptyList()
    ): X509CRL = certFactory.generateCrl(issuerCertificate, issuerKey, revokedSerials)

    fun sign(
        pdfBytes: ByteArray,
        nationalIdentityNumber: String,
        notBefore: Instant? = null,
        notAfter: Instant? = null,
        signingProfile: TestPdfSigner.SigningProfile = TestPdfSigner.SigningProfile.BASELINE_LT,
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
        return TestPdfSigner.signWithChain(
            pdfBytes = pdfBytes,
            chain = listOf(certificate, rootCertificate),
            signingKey = keyPair.private,
            signingProfile = signingProfile,
            tsaConfig = tsaConfig,
            crls = listOf(crlFor(rootCertificate, rootKeyPair.private))
        )
    }

    fun signWithRevokedCertificate(
        pdfBytes: ByteArray,
        nationalIdentityNumber: String,
        signingProfile: TestPdfSigner.SigningProfile = TestPdfSigner.SigningProfile.BASELINE_LT,
    ): ByteArray {
        val keyPair = certFactory.generateKeyPair()
        val certificate = certFactory.generateLeafCertificateWithNationalId(
            keyPair = keyPair,
            issuerKeyPair = rootKeyPair,
            issuerCertificate = rootCertificate,
            nationalIdentityNumber = nationalIdentityNumber,
            notBeforeOverride = null,
            notAfterOverride = null
        )
        val crl = crlFor(rootCertificate, rootKeyPair.private, revokedSerials = listOf(certificate.serialNumber))
        return TestPdfSigner.signWithChain(
            pdfBytes = pdfBytes,
            chain = listOf(certificate, rootCertificate),
            signingKey = keyPair.private,
            signingProfile = signingProfile,
            tsaConfig = tsaConfig,
            crls = listOf(crl)
        )
    }

    fun signWithUntrustedCertificate(
        pdfBytes: ByteArray,
        nationalIdentityNumber: String,
        signingProfile: TestPdfSigner.SigningProfile = TestPdfSigner.SigningProfile.BASELINE_LT,
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
        return TestPdfSigner.signWithChain(
            pdfBytes = pdfBytes,
            chain = listOf(certificate, selfSignedRoot),
            signingKey = leafKeyPair.private,
            signingProfile = signingProfile,
            tsaConfig = tsaConfig,
            crls = listOf(crlFor(selfSignedRoot, rootKeyPair.private))
        )
    }

    fun signWithoutNationalIdExtension(
        pdfBytes: ByteArray,
        signingProfile: TestPdfSigner.SigningProfile = TestPdfSigner.SigningProfile.BASELINE_LT,
    ): ByteArray {
        val keyPair = certFactory.generateKeyPair()
        val certificate = certFactory.generateLeafCertificateWithoutNationalId(
            keyPair = keyPair,
            issuerKeyPair = rootKeyPair,
            issuerCertificate = rootCertificate
        )
        return TestPdfSigner.signWithChain(
            pdfBytes = pdfBytes,
            chain = listOf(certificate, rootCertificate),
            signingKey = keyPair.private,
            signingProfile = signingProfile,
            tsaConfig = tsaConfig,
            crls = listOf(crlFor(rootCertificate, rootKeyPair.private))
        )
    }

    fun signWithUntrustedTimestamp(
        pdfBytes: ByteArray,
        nationalIdentityNumber: String,
        signingProfile: TestPdfSigner.SigningProfile = TestPdfSigner.SigningProfile.BASELINE_LT,
    ): ByteArray {
        val keyPair = certFactory.generateKeyPair()
        val certificate = certFactory.generateLeafCertificateWithNationalId(
            keyPair = keyPair,
            issuerKeyPair = rootKeyPair,
            issuerCertificate = rootCertificate,
            nationalIdentityNumber = nationalIdentityNumber,
            notBeforeOverride = null,
            notAfterOverride = null
        )
        val untrustedRootKeyPair = certFactory.generateKeyPair()
        val untrustedRoot = certFactory.generateSelfSignedRootCertificate(
            keyPair = untrustedRootKeyPair,
            templateCertificate = rootCertificate
        )
        val untrustedTsaKeyPair = certFactory.generateKeyPair()
        val untrustedTsaCert = certFactory.generateTsaCertificate(
            keyPair = untrustedTsaKeyPair,
            issuerKeyPair = untrustedRootKeyPair,
            issuerCertificate = untrustedRoot
        )
        val untrustedTsaConfig = TsaConfig(
            privateKey = untrustedTsaKeyPair.private,
            certificate = untrustedTsaCert,
            chain = listOf(untrustedTsaCert, untrustedRoot)
        )
        return TestPdfSigner.signWithChain(
            pdfBytes = pdfBytes,
            chain = listOf(certificate, rootCertificate),
            signingKey = keyPair.private,
            signingProfile = signingProfile,
            tsaConfig = untrustedTsaConfig,
            crls = listOf(
                crlFor(rootCertificate, rootKeyPair.private),
                crlFor(untrustedRoot, untrustedRootKeyPair.private)
            )
        )
    }
}
