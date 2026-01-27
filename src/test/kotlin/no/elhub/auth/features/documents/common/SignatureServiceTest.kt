package no.elhub.auth.features.documents.common

import eu.europa.esig.dss.enumerations.CertificationPermission
import eu.europa.esig.dss.enumerations.DigestAlgorithm
import eu.europa.esig.dss.enumerations.SignatureAlgorithm
import eu.europa.esig.dss.enumerations.SignatureLevel
import eu.europa.esig.dss.model.InMemoryDocument
import eu.europa.esig.dss.pades.signature.PAdESService
import eu.europa.esig.dss.pades.validation.PDFDocumentValidator
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.elhub.auth.features.common.httpTestClient
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.documents.EndUserSignatureTestHelper
import no.elhub.auth.features.documents.TestCertificateFactory
import no.elhub.auth.features.documents.TestCertificateUtil
import no.elhub.auth.features.documents.TestPdfSigner
import no.elhub.auth.features.documents.VaultTransitTestContainerExtension
import no.elhub.auth.features.documents.create.FileCertificateProvider
import no.elhub.auth.features.documents.create.FileCertificateProviderConfig
import no.elhub.auth.features.documents.create.HashicorpVaultSignatureProvider
import no.elhub.auth.features.documents.localVaultConfig
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Base64

class SignatureServiceTest : FunSpec({
    extensions(VaultTransitTestContainerExtension)

    val vaultSignatureProvider = HashicorpVaultSignatureProvider(
        client = httpTestClient,
        cfg = localVaultConfig
    )

    val certProviderConfig = FileCertificateProviderConfig(
        TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
        TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
        TestCertificateUtil.Constants.BANKID_ROOT_CERTIFICATE_LOCATION,
    )

    val certProvider = FileCertificateProvider(certProviderConfig)
    val padesService = PAdESService(CommonCertificateVerifier())
    val certFactory = TestCertificateFactory()

    val signingService = PdfSignatureService(padesService, certProvider, vaultSignatureProvider)

    val unsignedPdfBytes = this::class.java.classLoader.getResourceAsStream("unsigned.pdf")!!.readAllBytes()
    val nationalIdentityNumber = "01827535970"
    val endUserSignatureTestHelper = EndUserSignatureTestHelper(certFactory = certFactory)

    context("Test sign method") {
        test("Should add one signature with proper parameters") {

            val signedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()

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

            val signingCert = certProvider.getElhubSigningCertificate()
            certUsed.serialNumber shouldBe signingCert.serialNumber.toString()
            certUsed.certificateDN shouldBe signingCert.issuerX500Principal.name
        }

        test("Should invalidate signature when PDF is tampered with") {

            val signedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()

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
    }

    context("Test validateSignaturesAndReturnSignatory method") {

        test("Should return signatory when validation succeeds") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val bankIdSignedPdfBytes = endUserSignatureTestHelper.sign(
                pdfBytes = elhubSignedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )

            val pdfValidationResult = signingService.validateSignaturesAndReturnSignatory(bankIdSignedPdfBytes, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeRight(PartyIdentifier(idType = PartyIdentifierType.NationalIdentityNumber, idValue = nationalIdentityNumber))
        }

        test("Should return MissingElhubSignature when validating a pdf without Elhub signature") {
            val bankIdSignedPdfBytes = endUserSignatureTestHelper.sign(
                pdfBytes = unsignedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )

            val pdfValidationResult = signingService.validateSignaturesAndReturnSignatory(bankIdSignedPdfBytes, bankIdSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.MissingElhubSignature)
        }

        test("Should return InvalidElhubSignature when pdf is tampered with") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val tamperedPdfBytes = TestPdfSigner.tamperPdf(elhubSignedPdfBytes)
            val bankIdSignedPdf = endUserSignatureTestHelper.sign(tamperedPdfBytes, nationalIdentityNumber = nationalIdentityNumber)
            val pdfValidationResult = signingService.validateSignaturesAndReturnSignatory(bankIdSignedPdf, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.InvalidElhubSignature)
        }

        test("Should return MissingBankIdSignature when pdf doesn't have a bankId signature") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val pdfValidationResult = signingService.validateSignaturesAndReturnSignatory(elhubSignedPdfBytes, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.MissingBankIdSignature)
        }

        test("Should return InvalidBankIdSignature when pdf doesn't have a bankId signature") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val bankIdSignedPdfBytes = endUserSignatureTestHelper.sign(
                pdfBytes = elhubSignedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )
            val tamperedBankIdSignature = TestPdfSigner.tamperLatestSignature(bankIdSignedPdfBytes)

            val pdfValidationResult = signingService.validateSignaturesAndReturnSignatory(tamperedBankIdSignature, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.InvalidBankIdSignature)
        }

        test("Should return BankIdSigningCertNotFromExpectedRoot for untrusted bankId root") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val untrustedSignedPdfBytes = endUserSignatureTestHelper.signWithUntrustedCertificate(
                pdfBytes = elhubSignedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )

            val pdfValidationResult = signingService.validateSignaturesAndReturnSignatory(untrustedSignedPdfBytes, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.BankIdSigningCertNotFromExpectedRoot)
        }

        test("Should return MissingNationalId when bankId signature lacks national id extension") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val bankIdSignedPdfBytes = endUserSignatureTestHelper.signWithoutNationalIdExtension(
                pdfBytes = elhubSignedPdfBytes
            )

            val pdfValidationResult = signingService.validateSignaturesAndReturnSignatory(bankIdSignedPdfBytes, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.MissingNationalId)
        }

        test("Should return ElhubSigningCertNotTrusted when Elhub signing cert isn't trusted") {
            val fakeElhubCerts = certFactory.generateFakeElhubCertificates(certProvider.getElhubSigningCertificate())
            val elhubSignedPdfBytes = TestPdfSigner.signWithCertificate(
                pdfBytes = unsignedPdfBytes,
                signingCert = fakeElhubCerts.signingCert,
                chain = fakeElhubCerts.chain,
                signingKey = fakeElhubCerts.signingKey.private
            )
            val bankIdSignedPdfBytes = endUserSignatureTestHelper.sign(
                pdfBytes = elhubSignedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )

            val pdfValidationResult = signingService.validateSignaturesAndReturnSignatory(bankIdSignedPdfBytes, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.ElhubSigningCertNotTrusted)
        }

        test("Should return OriginalDocumentMismatch when signed PDF does not match stored Elhub-signed PDF") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val bankIdSignedPdfBytes = endUserSignatureTestHelper.sign(
                pdfBytes = elhubSignedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )

            val originalElhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()

            val pdfValidationResult = signingService.validateSignaturesAndReturnSignatory(bankIdSignedPdfBytes, originalElhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.OriginalDocumentMismatch)
        }
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
