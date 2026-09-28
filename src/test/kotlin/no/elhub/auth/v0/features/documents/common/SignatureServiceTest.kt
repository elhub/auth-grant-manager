package no.elhub.auth.v0.features.documents.common

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.signatures.SignatureUtil
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.elhub.auth.common.documents.pdf.FileCertificateProvider
import no.elhub.auth.common.documents.pdf.FileCertificateProviderConfig
import no.elhub.auth.common.documents.pdf.HashicorpVaultSignatureProvider
import no.elhub.auth.common.documents.pdf.ITextPdfSignatureService
import no.elhub.auth.v0.features.common.httpTestClient
import no.elhub.auth.v0.features.common.party.PartyIdentifier
import no.elhub.auth.v0.features.common.party.PartyIdentifierType
import no.elhub.auth.v0.features.documents.EndUserSignatureTestHelper
import no.elhub.auth.v0.features.documents.TempBankIdCertificatesLocation
import no.elhub.auth.v0.features.documents.TestCertificateFactory
import no.elhub.auth.v0.features.documents.TestCertificateUtil
import no.elhub.auth.v0.features.documents.VaultTransitTestContainerExtension
import no.elhub.auth.v0.features.documents.localVaultConfig

class SignatureServiceTest : FunSpec({
    extensions(VaultTransitTestContainerExtension)

    val tempBankIdCerts = TempBankIdCertificatesLocation.create()
    val certificateProvider = FileCertificateProvider(
        FileCertificateProviderConfig(
            TestCertificateUtil.Constants.INTERMEDIATE_CERTIFICATE_LOCATION,
            TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
            tempBankIdCerts.bankIdRootCertificatesDir,
            tempBankIdCerts.bankIdRootCertificatesDir,
        )
    )
    val vaultSignatureProvider = HashicorpVaultSignatureProvider(httpTestClient, localVaultConfig)
    val commonSignatureService = ITextPdfSignatureService(certificateProvider, vaultSignatureProvider)
    val signatureService = V0SignatureServiceAdapter(commonSignatureService, commonSignatureService)
    val unsignedPdf = this::class.java.classLoader.getResourceAsStream("unsigned.pdf")!!.readAllBytes()
    val nationalIdentityNumber = "01827535970"
    val endUserSignatureHelper = EndUserSignatureTestHelper(
        certFactory = TestCertificateFactory(
            bankIdRootCertificatePath = tempBankIdCerts.bankIdRootCertificatePath,
            bankIdRootPrivateKeyPath = tempBankIdCerts.bankIdRootPrivateKeyPath,
        )
    )

    test("signs through the v0 signature service") {
        val signedPdf = signatureService.sign(unsignedPdf).shouldBeRight()

        PdfDocument(PdfReader(signedPdf.inputStream())).use { document ->
            val signatureUtil = SignatureUtil(document)
            val signatureNames = signatureUtil.signatureNames
            signatureNames.size shouldBe 1
            signatureUtil.readSignatureData(signatureNames.single())
                .verifySignatureIntegrityAndAuthenticity() shouldBe true
        }
    }

    test("validates through the v0 signature service and returns a party identifier") {
        val elhubSignedPdf = signatureService.sign(unsignedPdf).shouldBeRight()
        val bankIdSignedPdf = endUserSignatureHelper.sign(elhubSignedPdf, nationalIdentityNumber)

        signatureService.validateSignaturesAndReturnSignatory(bankIdSignedPdf, elhubSignedPdf).shouldBeRight(
            PartyIdentifier(
                idType = PartyIdentifierType.NationalIdentityNumber,
                idValue = nationalIdentityNumber,
            )
        )
    }
})
