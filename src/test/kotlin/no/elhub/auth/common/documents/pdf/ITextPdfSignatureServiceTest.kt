package no.elhub.auth.common.documents.pdf

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.signatures.SignatureUtil
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.elhub.auth.v0.features.common.httpTestClient
import no.elhub.auth.v0.features.documents.EndUserSignatureTestHelper
import no.elhub.auth.v0.features.documents.TempBankIdCertificatesLocation
import no.elhub.auth.v0.features.documents.TestCertificateFactory
import no.elhub.auth.v0.features.documents.TestCertificateUtil
import no.elhub.auth.v0.features.documents.TestPdfSigner
import no.elhub.auth.v0.features.documents.VaultTransitTestContainerExtension

class ITextPdfSignatureServiceTest : FunSpec({
    extensions(VaultTransitTestContainerExtension)

    val vaultSignatureProvider = HashicorpVaultSignatureProvider(httpTestClient, localVaultConfig())
    val tempBankIdCerts = TempBankIdCertificatesLocation.create()
    val certificateProvider = FileCertificateProvider(
        FileCertificateProviderConfig(
            TestCertificateUtil.Constants.INTERMEDIATE_CERTIFICATE_LOCATION,
            TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
            tempBankIdCerts.bankIdRootCertificatesDir,
            tempBankIdCerts.bankIdRootCertificatesDir,
        )
    )
    val certFactory = TestCertificateFactory(
        bankIdRootCertificatePath = tempBankIdCerts.bankIdRootCertificatePath,
        bankIdRootPrivateKeyPath = tempBankIdCerts.bankIdRootPrivateKeyPath,
    )
    val service = ITextPdfSignatureService(certificateProvider, vaultSignatureProvider)
    val unsignedPdf = this::class.java.classLoader.getResourceAsStream("unsigned.pdf")!!.readAllBytes()
    val nationalIdentityNumber = "01827535970"
    val endUserSignatureHelper = EndUserSignatureTestHelper(certFactory = certFactory)

    test("signs a PDF with the Elhub certificate") {
        val signedPdf = service.sign(unsignedPdf)

        PdfDocument(PdfReader(signedPdf.inputStream())).use { document ->
            val signatureUtil = SignatureUtil(document)
            val signatureName = signatureUtil.signatureNames.single()
            val signature = signatureUtil.getSignature(signatureName)
            signature.subFilter shouldBe PdfName.ETSI_CAdES_DETACHED
            signatureUtil.readSignatureData(signatureName).verifySignatureIntegrityAndAuthenticity() shouldBe true
        }
    }

    test("returns the signatory from a valid signed PDF") {
        val elhubSignedPdf = service.sign(unsignedPdf)
        val endUserSignedPdf = endUserSignatureHelper.sign(elhubSignedPdf, nationalIdentityNumber)

        service.validateSignaturesAndReturnSignatory(endUserSignedPdf, elhubSignedPdf) shouldBe
            PdfSignatory(nationalIdentityNumber)
    }

    test("rejects a PDF without an Elhub signature") {
        val endUserSignedPdf = endUserSignatureHelper.sign(unsignedPdf, nationalIdentityNumber)

        shouldThrow<PdfValidationException.MissingElhubSignature> {
            service.validateSignaturesAndReturnSignatory(endUserSignedPdf, unsignedPdf)
        }
    }

    test("rejects a signed PDF that does not match the original Elhub-signed PDF") {
        val elhubSignedPdf = service.sign(unsignedPdf)
        val endUserSignedPdf = endUserSignatureHelper.sign(elhubSignedPdf, nationalIdentityNumber)
        val differentOriginal = service.sign(unsignedPdf)

        differentOriginal shouldNotBe elhubSignedPdf

        shouldThrow<PdfValidationException.OriginalDocumentMismatch> {
            service.validateSignaturesAndReturnSignatory(endUserSignedPdf, differentOriginal)
        }
    }

    test("accepts a valid PAdES-LT end-user signature") {
        val elhubSignedPdf = service.sign(unsignedPdf)
        val endUserSignedPdf = endUserSignatureHelper.sign(
            pdfBytes = elhubSignedPdf,
            nationalIdentityNumber = nationalIdentityNumber,
            signingProfile = TestPdfSigner.SigningProfile.BASELINE_LT,
        )

        service.validateSignaturesAndReturnSignatory(endUserSignedPdf, elhubSignedPdf) shouldBe
            PdfSignatory(nationalIdentityNumber)
    }

    test("rejects a PAdES-B-T end-user signature") {
        val elhubSignedPdf = service.sign(unsignedPdf)
        val endUserSignedPdf = endUserSignatureHelper.sign(
            pdfBytes = elhubSignedPdf,
            nationalIdentityNumber = nationalIdentityNumber,
            signingProfile = TestPdfSigner.SigningProfile.BASELINE_T,
        )

        shouldThrow<PdfValidationException.BankIdSignatureNotPadesLT> {
            service.validateSignaturesAndReturnSignatory(endUserSignedPdf, elhubSignedPdf)
        }
    }

    test("rejects an end-user signature with an untrusted timestamp") {
        val elhubSignedPdf = service.sign(unsignedPdf)
        val endUserSignedPdf = endUserSignatureHelper.signWithUntrustedTimestamp(
            pdfBytes = elhubSignedPdf,
            nationalIdentityNumber = nationalIdentityNumber,
        )

        shouldThrow<PdfValidationException.MissingTrustedTimestamp> {
            service.validateSignaturesAndReturnSignatory(endUserSignedPdf, elhubSignedPdf)
        }
    }

    test("rejects a revoked end-user certificate") {
        val elhubSignedPdf = service.sign(unsignedPdf)
        val endUserSignedPdf = endUserSignatureHelper.signWithRevokedCertificate(
            pdfBytes = elhubSignedPdf,
            nationalIdentityNumber = nationalIdentityNumber,
        )

        shouldThrow<PdfValidationException.BankIdCertificateRevoked> {
            service.validateSignaturesAndReturnSignatory(endUserSignedPdf, elhubSignedPdf)
        }
    }

    test("accepts signatures from multiple trusted BankID roots") {
        val elhubSignedPdf = service.sign(unsignedPdf)
        val firstSignedPdf = endUserSignatureHelper.sign(elhubSignedPdf, nationalIdentityNumber)
        val secondRoot = tempBankIdCerts.getRoot(1)
        val secondHelper = EndUserSignatureTestHelper(
            certFactory = TestCertificateFactory(
                bankIdRootCertificatePath = secondRoot.certificatePath,
                bankIdRootPrivateKeyPath = secondRoot.privateKeyPath,
            )
        )
        val secondSignedPdf = secondHelper.sign(elhubSignedPdf, nationalIdentityNumber)

        service.validateSignaturesAndReturnSignatory(firstSignedPdf, elhubSignedPdf) shouldBe
            PdfSignatory(nationalIdentityNumber)
        service.validateSignaturesAndReturnSignatory(secondSignedPdf, elhubSignedPdf) shouldBe
            PdfSignatory(nationalIdentityNumber)
    }

    test("rejects a BankID certificate from an untrusted root") {
        val elhubSignedPdf = service.sign(unsignedPdf)
        val endUserSignedPdf = endUserSignatureHelper.signWithUntrustedCertificate(elhubSignedPdf, nationalIdentityNumber)

        shouldThrow<PdfValidationException.BankIdSigningCertNotFromExpectedRoot> {
            service.validateSignaturesAndReturnSignatory(endUserSignedPdf, elhubSignedPdf)
        }
    }

    test("rejects a BankID certificate without a national identity number") {
        val elhubSignedPdf = service.sign(unsignedPdf)
        val endUserSignedPdf = endUserSignatureHelper.signWithoutNationalIdExtension(elhubSignedPdf)

        shouldThrow<PdfValidationException.MissingNationalId> {
            service.validateSignaturesAndReturnSignatory(endUserSignedPdf, elhubSignedPdf)
        }
    }

    test("rejects an Elhub signature made with an untrusted certificate") {
        val fakeElhubCerts = certFactory.generateFakeElhubCertificates(certificateProvider.getElhubSigningCertificate())
        val elhubSignedPdf = TestPdfSigner.signWithChain(
            pdfBytes = unsignedPdf,
            chain = fakeElhubCerts.chain,
            signingKey = fakeElhubCerts.signingKey.private,
        )
        val endUserSignedPdf = endUserSignatureHelper.sign(elhubSignedPdf, nationalIdentityNumber)

        shouldThrow<PdfValidationException.ElhubSigningCertNotTrusted> {
            service.validateSignaturesAndReturnSignatory(endUserSignedPdf, elhubSignedPdf)
        }
    }

    test("rejects a BankID certificate that is invalid at the trusted timestamp") {
        val elhubSignedPdf = service.sign(unsignedPdf)
        val endUserSignedPdf = endUserSignatureHelper.sign(
            pdfBytes = elhubSignedPdf,
            nationalIdentityNumber = nationalIdentityNumber,
            notAfter = java.time.Instant.now().minusSeconds(3600),
        )

        shouldThrow<PdfValidationException.BankIdSigningCertNotValidAtTimestamp> {
            service.validateSignaturesAndReturnSignatory(endUserSignedPdf, elhubSignedPdf)
        }
    }

    test("rejects a tampered Elhub signature") {
        val elhubSignedPdf = service.sign(unsignedPdf)
        val tamperedPdf = TestPdfSigner.tamperPdf(elhubSignedPdf)
        val endUserSignedPdf = endUserSignatureHelper.sign(tamperedPdf, nationalIdentityNumber)

        shouldThrow<PdfValidationException.InvalidElhubSignature> {
            service.validateSignaturesAndReturnSignatory(endUserSignedPdf, elhubSignedPdf)
        }
    }

    test("rejects annotations added between Elhub and BankID signatures") {
        val elhubSignedPdf = service.sign(unsignedPdf)
        val modifiedPdf = TestPdfSigner.addAnnotationIncremental(elhubSignedPdf)
        val endUserSignedPdf = endUserSignatureHelper.sign(modifiedPdf, nationalIdentityNumber)

        shouldThrow<PdfValidationException.ElhubSignatureModifiedAfterSigning> {
            service.validateSignaturesAndReturnSignatory(endUserSignedPdf, elhubSignedPdf)
        }
    }

    test("rejects pages added between Elhub and BankID signatures") {
        val elhubSignedPdf = service.sign(unsignedPdf)
        val modifiedPdf = TestPdfSigner.addPageIncremental(elhubSignedPdf)
        val endUserSignedPdf = endUserSignatureHelper.sign(modifiedPdf, nationalIdentityNumber)

        shouldThrow<PdfValidationException.ElhubSignatureModifiedAfterSigning> {
            service.validateSignaturesAndReturnSignatory(endUserSignedPdf, elhubSignedPdf)
        }
    }

    test("rejects visual changes added between Elhub and BankID signatures") {
        val elhubSignedPdf = service.sign(unsignedPdf)
        val modifiedPdf = TestPdfSigner.addVisualChangeIncremental(elhubSignedPdf)
        val endUserSignedPdf = endUserSignatureHelper.sign(modifiedPdf, nationalIdentityNumber)

        shouldThrow<PdfValidationException.ElhubSignatureModifiedAfterSigning> {
            service.validateSignaturesAndReturnSignatory(endUserSignedPdf, elhubSignedPdf)
        }
    }

    test("rejects an invalid BankID signature") {
        val elhubSignedPdf = service.sign(unsignedPdf)
        val endUserSignedPdf = endUserSignatureHelper.sign(elhubSignedPdf, nationalIdentityNumber)
        val tamperedPdf = TestPdfSigner.tamperLatestSignature(endUserSignedPdf)

        shouldThrow<PdfValidationException.InvalidBankIdSignature> {
            service.validateSignaturesAndReturnSignatory(tamperedPdf, elhubSignedPdf)
        }
    }

    test("rejects a PDF without a BankID signature") {
        val elhubSignedPdf = service.sign(unsignedPdf)

        shouldThrow<PdfValidationException.MissingBankIdSignature> {
            service.validateSignaturesAndReturnSignatory(elhubSignedPdf, elhubSignedPdf)
        }
    }
})

private fun localVaultConfig() = VaultConfig(
    url = "http://localhost:8200/v1/transit",
    key = "test-key",
    tokenPath = "src/test/resources/vault_token_mock.txt",
)
