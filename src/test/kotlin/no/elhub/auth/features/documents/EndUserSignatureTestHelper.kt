package no.elhub.auth.features.documents

import eu.europa.esig.dss.enumerations.SignatureLevel
import eu.europa.esig.dss.spi.x509.tsp.KeyEntityTSPSource
import java.time.Duration
import java.time.Instant

// Test helper to mock external/end-user signatures during the document flow.
class EndUserSignatureTestHelper(
    private val signatureLevel: SignatureLevel = SignatureLevel.PAdES_BASELINE_T,
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
    private val tspSource = KeyEntityTSPSource(
        tsaKeyPair.private,
        tsaCertificate,
        listOf(tsaCertificate, rootCertificate)
    ).apply {
        setTsaPolicy("1.2.3.4.5")
    }

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
            signatureLevel = signatureLevel,
            tspSource = tspSource
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
            signatureLevel = signatureLevel,
            tspSource = tspSource
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
            signatureLevel = signatureLevel,
            tspSource = tspSource
        )
    }

    fun signWithUntrustedTimestamp(
        pdfBytes: ByteArray,
        nationalIdentityNumber: String
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
        val untrustedTspSource = KeyEntityTSPSource(
            untrustedTsaKeyPair.private,
            untrustedTsaCert,
            listOf(untrustedTsaCert, untrustedRoot)
        ).apply {
            setTsaPolicy("1.2.3.4.5")
        }
        return TestPdfSigner.signWithCertificate(
            pdfBytes = pdfBytes,
            signingCert = certificate,
            chain = listOf(certificate, rootCertificate),
            signingKey = keyPair.private,
            signatureLevel = signatureLevel,
            tspSource = untrustedTspSource
        )
    }
}
