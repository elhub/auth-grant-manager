package no.elhub.auth.features.documents.create

import eu.europa.esig.dss.enumerations.CertificationPermission
import eu.europa.esig.dss.enumerations.DigestAlgorithm
import eu.europa.esig.dss.enumerations.SignatureAlgorithm
import eu.europa.esig.dss.enumerations.SignatureLevel
import eu.europa.esig.dss.model.InMemoryDocument
import eu.europa.esig.dss.pades.signature.PAdESService
import eu.europa.esig.dss.pades.validation.PDFDocumentValidator
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.elhub.auth.config.loadCerts
import no.elhub.auth.features.common.httpTestClient
import no.elhub.auth.features.documents.TestCertificateUtil
import no.elhub.auth.features.documents.VaultTransitTestContainerExtension
import no.elhub.auth.features.documents.localVaultConfig
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Base64

class SigningServiceTest : FunSpec({
    extensions(VaultTransitTestContainerExtension)

    val vaultSignatureProvider = HashicorpVaultSignatureProvider(
        client = httpTestClient,
        cfg = localVaultConfig
    )

    val signingCertificate: SigningCertificate =
        loadCerts(File(TestCertificateUtil.Constants.CERTIFICATE_LOCATION)).single()

    val signingCertificateChain: SigningCertificateChain =
        loadCerts(File(TestCertificateUtil.Constants.CERTIFICATE_LOCATION))

    val padesService = PAdESService(CommonCertificateVerifier())

    val signingService =
        PAdESDocumentSigningService(signingCertificate, signingCertificateChain, padesService)
    val unsignedPdfBytes = this::class.java.classLoader.getResourceAsStream("unsigned.pdf")!!.readAllBytes()

    test("Should add one signature with proper parameters") {
        val dataToSign = signingService.getDataToSign(unsignedPdfBytes).shouldBeRight()

        val signatureBytes = vaultSignatureProvider.fetchSignature(dataToSign).shouldBeRight()

        val signedPdfBytes = signingService.sign(unsignedPdfBytes, signatureBytes).shouldBeRight()

        val signedPdf = InMemoryDocument(signedPdfBytes)

        val reports = PDFDocumentValidator(signedPdf).apply {
            setCertificateVerifier(CommonCertificateVerifier())
        }.validateDocument()

        val simpleReport = reports.simpleReport
        val diagnosticData = reports.diagnosticData

        // Assert that PDF contains exactly one signature
        simpleReport.signatureIdList.size shouldBe 1

        val signature = diagnosticData.signatures.first()

        // Validate signature parameters
        signature.digestAlgorithm shouldBe DigestAlgorithm.SHA256
        signature.signatureAlgorithm shouldBe SignatureAlgorithm.RSA_SHA256
        signature.signatureFormat shouldBe SignatureLevel.PAdES_BASELINE_B
        signature.pdfRevision.docMDPPermissions shouldBe CertificationPermission.MINIMAL_CHANGES_PERMITTED
        signature.isBLevelTechnicallyValid shouldBe true

        // Validate that the signing certificate matches the expected certificate
        val certRef = signature.signingCertificateReference.certificateId
        val certUsed = diagnosticData.getUsedCertificateById(certRef)
        certUsed.serialNumber shouldBe signingCertificate.serialNumber.toString()
        certUsed.certificateDN shouldBe signingCertificate.issuerX500Principal.name
    }

    test("Should invalidate signature when PDF is tampered with") {
        val dataToSign = signingService.getDataToSign(unsignedPdfBytes).shouldBeRight()

        val signatureBytes = vaultSignatureProvider.fetchSignature(dataToSign).shouldBeRight()

        val signedPdfBytes = signingService.sign(unsignedPdfBytes, signatureBytes).shouldBeRight()

        val signedPdf = InMemoryDocument(signedPdfBytes)

        val preTamperReport = PDFDocumentValidator(signedPdf).apply {
            setCertificateVerifier(CommonCertificateVerifier())
        }.validateDocument()

        val originalSignature = preTamperReport.diagnosticData.signatures.first()

        // Verify that the signature is initially valid
        originalSignature.isBLevelTechnicallyValid shouldBe true

        // Compute the digest from the signed byte range and compare it with the embedded one
        val byteRange = originalSignature.signatureByteRange
        val expectedDigestBase64 = computeDigestOverByteRange(signedPdfBytes, byteRange)
        val embeddedDigestBase64 = Base64.getEncoder().encodeToString(originalSignature.messageDigest.digestValue)
        expectedDigestBase64 shouldBe embeddedDigestBase64

        // Tamper with the signed PDF by changing a byte outside the signature placeholder
        val tamperedPdfBytes = signedPdfBytes.copyOf().apply {
            this[100] = (this[100] + 1).toByte()
        }

        val tamperedPdf = InMemoryDocument(tamperedPdfBytes)
        val tamperedReport = PDFDocumentValidator(tamperedPdf).apply {
            setCertificateVerifier(CommonCertificateVerifier())
        }.validateDocument()

        val tamperedSignature = tamperedReport.diagnosticData.signatures.first()

        // Ensure that the ByteRange used for signing is still the same
        tamperedSignature.signatureByteRange shouldBe byteRange

        // The tampering should cause signature validation to fail
        tamperedSignature.isBLevelTechnicallyValid shouldBe false

        // Confirm that the digest of the tampered content no longer matches the embedded digest
        val tamperedDigestBase64 = computeDigestOverByteRange(tamperedPdfBytes, byteRange)
        val tamperedEmbeddedDigestBase64 =
            Base64.getEncoder().encodeToString(tamperedSignature.messageDigest.digestValue)

        tamperedDigestBase64 shouldNotBe tamperedEmbeddedDigestBase64
    }
})

fun computeDigestOverByteRange(documentBytes: ByteArray, byteRange: List<BigInteger>): String {
    val firstSegment = documentBytes.sliceArray(byteRange[0].toInt() until byteRange[1].toInt())
    val secondSegment = documentBytes.sliceArray(byteRange[2].toInt() until byteRange[2].toInt() + byteRange[3].toInt())
    val digest = MessageDigest.getInstance("SHA-256").apply {
        update(firstSegment)
        update(secondSegment)
    }.digest()
    return Base64.getEncoder().encodeToString(digest)
}
