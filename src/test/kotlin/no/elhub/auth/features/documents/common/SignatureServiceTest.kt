package no.elhub.auth.features.documents.common

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.signatures.PdfSigner
import com.itextpdf.signatures.SignatureUtil
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.elhub.auth.features.common.httpTestClient
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.documents.EndUserSignatureTestHelper
import no.elhub.auth.features.documents.TempBankIdCertificatesLocation
import no.elhub.auth.features.documents.TestCertificateFactory
import no.elhub.auth.features.documents.TestCertificateUtil
import no.elhub.auth.features.documents.TestPdfSigner
import no.elhub.auth.features.documents.VaultTransitTestContainerExtension
import no.elhub.auth.features.documents.create.FileCertificateProvider
import no.elhub.auth.features.documents.create.FileCertificateProviderConfig
import no.elhub.auth.features.documents.create.HashicorpVaultSignatureProvider
import no.elhub.auth.features.documents.localVaultConfig
import java.nio.file.Files

class SignatureServiceTest : FunSpec({
    extensions(VaultTransitTestContainerExtension)

    val vaultSignatureProvider = HashicorpVaultSignatureProvider(
        client = httpTestClient,
        cfg = localVaultConfig
    )

    val tempBankIdCerts = TempBankIdCertificatesLocation.create()

    val certProviderConfig = FileCertificateProviderConfig(
        TestCertificateUtil.Constants.INTERMEDIATE_CERTIFICATE_LOCATION,
        TestCertificateUtil.Constants.CERTIFICATE_LOCATION,
        tempBankIdCerts.bankIdRootCertificatesDir,
        tempBankIdCerts.bankIdRootCertificatesDir,
    )

    val certProvider = FileCertificateProvider(certProviderConfig)
    val certFactory = TestCertificateFactory(
        bankIdRootCertificatePath = tempBankIdCerts.bankIdRootCertificatePath,
        bankIdRootPrivateKeyPath = tempBankIdCerts.bankIdRootPrivateKeyPath
    )

    val signingService = ITextPdfSignatureService(certProvider, vaultSignatureProvider)

    val unsignedPdfBytes = this::class.java.classLoader.getResourceAsStream("unsigned.pdf")!!.readAllBytes()
    val nationalIdentityNumber = "01827535970"
    val endUserSignatureTestHelper = EndUserSignatureTestHelper(certFactory = certFactory)

    context("Test sign method") {
        test("Should add one signature with proper parameters") {

            val signedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()

            PdfDocument(PdfReader(signedPdfBytes.inputStream())).use { pdfDocument ->
                val signatureUtil = SignatureUtil(pdfDocument)
                val signatureNames = signatureUtil.signatureNames

                signatureNames.size shouldBe 1

                val signatureName = signatureNames.single()
                signatureUtil.signatureCoversWholeDocument(signatureName) shouldBe true

                val signatureDictionary = signatureUtil.getSignature(signatureName).shouldNotBeNull()
                val pkcs7 = signatureUtil.readSignatureData(signatureName)

                pkcs7.digestAlgorithmName shouldBe "SHA256"
                pkcs7.signatureMechanismName shouldBe "SHA256withRSA"
                signatureDictionary.subFilter shouldBe PdfName.ETSI_CAdES_DETACHED
                pkcs7.verifySignatureIntegrityAndAuthenticity() shouldBe true

                val docMdpReference = pdfDocument.catalog.pdfObject
                    .getAsDictionary(PdfName.Perms)
                    ?.getAsDictionary(PdfName.DocMDP)
                docMdpReference.shouldNotBeNull()

                val signingCert = certProvider.getElhubSigningCertificate()
                val actualSigningCertificate = pkcs7.signingCertificate.shouldNotBeNull()
                actualSigningCertificate.serialNumber shouldBe signingCert.serialNumber
                actualSigningCertificate.issuerX500Principal shouldBe signingCert.issuerX500Principal
            }
        }

        test("Should invalidate signature when PDF is tampered with") {

            val signedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val originalByteRange = PdfDocument(PdfReader(signedPdfBytes.inputStream())).use { pdfDocument ->
                val signatureUtil = SignatureUtil(pdfDocument)
                val signatureName = signatureUtil.signatureNames.single()
                val pkcs7 = signatureUtil.readSignatureData(signatureName)
                pkcs7.verifySignatureIntegrityAndAuthenticity() shouldBe true

                signatureUtil.getSignature(signatureName)
                    .shouldNotBeNull()
                    .byteRange
                    .toLongArray()
                    .toList()
            }

            // Tamper with the signed PDF by changing a byte outside the signature placeholder
            val tamperedPdfBytes = signedPdfBytes.copyOf().apply {
                this[100] = (this[100] + 1).toByte()
            }

            PdfDocument(PdfReader(tamperedPdfBytes.inputStream())).use { pdfDocument ->
                val signatureUtil = SignatureUtil(pdfDocument)
                val signatureName = signatureUtil.signatureNames.single()
                val tamperedSignature = signatureUtil.getSignature(signatureName).shouldNotBeNull()
                val tamperedByteRange = tamperedSignature.byteRange.toLongArray().toList()

                tamperedByteRange shouldBe originalByteRange

                val pkcs7 = signatureUtil.readSignatureData(signatureName)
                pkcs7.verifySignatureIntegrityAndAuthenticity() shouldBe false
            }
        }
    }

    context("Test validateSignaturesAndReturnSignatory method") {

        test("Should return signatory when validation succeeds") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val bankIdSignedPdfBytes = endUserSignatureTestHelper.sign(
                pdfBytes = elhubSignedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )

            val pdfValidationResult =
                signingService.validateSignaturesAndReturnSignatory(bankIdSignedPdfBytes, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeRight(
                PartyIdentifier(
                    idType = PartyIdentifierType.NationalIdentityNumber,
                    idValue = nationalIdentityNumber
                )
            )
        }

        test("Should accept signatures from multiple trusted BankID roots") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()

            val bankIdSignedFromRoot1 = endUserSignatureTestHelper.sign(
                pdfBytes = elhubSignedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )

            val secondRoot = tempBankIdCerts.getRoot(1)
            val certFactory2 = TestCertificateFactory(
                bankIdRootCertificatePath = secondRoot.certificatePath,
                bankIdRootPrivateKeyPath = secondRoot.privateKeyPath
            )
            val endUserSignatureHelper2 = EndUserSignatureTestHelper(certFactory = certFactory2)
            val bankIdSignedFromRoot2 = endUserSignatureHelper2.sign(
                pdfBytes = elhubSignedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )

            signingService.validateSignaturesAndReturnSignatory(bankIdSignedFromRoot1, elhubSignedPdfBytes)
                .shouldBeRight(
                    PartyIdentifier(
                        idType = PartyIdentifierType.NationalIdentityNumber,
                        idValue = nationalIdentityNumber
                    )
                )

            signingService.validateSignaturesAndReturnSignatory(bankIdSignedFromRoot2, elhubSignedPdfBytes)
                .shouldBeRight(
                    PartyIdentifier(
                        idType = PartyIdentifierType.NationalIdentityNumber,
                        idValue = nationalIdentityNumber
                    )
                )
        }

        test("Should return MissingElhubSignature when validating a pdf without Elhub signature") {
            val bankIdSignedPdfBytes = endUserSignatureTestHelper.sign(
                pdfBytes = unsignedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )

            val pdfValidationResult =
                signingService.validateSignaturesAndReturnSignatory(bankIdSignedPdfBytes, bankIdSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.MissingElhubSignature)
        }

        test("Should return InvalidElhubSignature when pdf is tampered with") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val tamperedPdfBytes = TestPdfSigner.tamperPdf(elhubSignedPdfBytes)
            val bankIdSignedPdf =
                endUserSignatureTestHelper.sign(tamperedPdfBytes, nationalIdentityNumber = nationalIdentityNumber)
            val pdfValidationResult =
                signingService.validateSignaturesAndReturnSignatory(bankIdSignedPdf, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.InvalidElhubSignature)
        }

        test("Should return ElhubSignatureModifiedAfterSigning when annotations are added after Elhub signature") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val annotatedPdfBytes = TestPdfSigner.addAnnotationIncremental(elhubSignedPdfBytes)
            val bankIdSignedPdfBytes = endUserSignatureTestHelper.sign(
                pdfBytes = annotatedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )

            val pdfValidationResult =
                signingService.validateSignaturesAndReturnSignatory(bankIdSignedPdfBytes, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.ElhubSignatureModifiedAfterSigning)
        }

        test("Should return ElhubSignatureModifiedAfterSigning when pages are added after Elhub signature") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val extraPagePdfBytes = TestPdfSigner.addPageIncremental(elhubSignedPdfBytes)
            val bankIdSignedPdfBytes = endUserSignatureTestHelper.sign(
                pdfBytes = extraPagePdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )

            val pdfValidationResult =
                signingService.validateSignaturesAndReturnSignatory(bankIdSignedPdfBytes, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.ElhubSignatureModifiedAfterSigning)
        }

        test("Should return ElhubSignatureModifiedAfterSigning when visual changes are added after Elhub signature") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val visuallyChangedPdfBytes = TestPdfSigner.addVisualChangeIncremental(elhubSignedPdfBytes)
            val bankIdSignedPdfBytes = endUserSignatureTestHelper.sign(
                pdfBytes = visuallyChangedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )

            val pdfValidationResult =
                signingService.validateSignaturesAndReturnSignatory(bankIdSignedPdfBytes, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.ElhubSignatureModifiedAfterSigning)
        }

        test("Should return MissingBankIdSignature when pdf doesn't have a bankId signature") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val pdfValidationResult =
                signingService.validateSignaturesAndReturnSignatory(elhubSignedPdfBytes, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.MissingBankIdSignature)
        }

        test("Should return InvalidBankIdSignature when pdf doesn't have a bankId signature") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val bankIdSignedPdfBytes = endUserSignatureTestHelper.sign(
                pdfBytes = elhubSignedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )
            val tamperedBankIdSignature = TestPdfSigner.tamperLatestSignature(bankIdSignedPdfBytes)

            val pdfValidationResult =
                signingService.validateSignaturesAndReturnSignatory(tamperedBankIdSignature, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.InvalidBankIdSignature)
        }

        test("Should return BankIdSigningCertNotFromExpectedRoot for untrusted bankId root") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val untrustedSignedPdfBytes = endUserSignatureTestHelper.signWithUntrustedCertificate(
                pdfBytes = elhubSignedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )

            val pdfValidationResult =
                signingService.validateSignaturesAndReturnSignatory(untrustedSignedPdfBytes, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.BankIdSigningCertNotFromExpectedRoot)
        }

        test("Should return MissingNationalId when bankId signature lacks national id extension") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val bankIdSignedPdfBytes = endUserSignatureTestHelper.signWithoutNationalIdExtension(
                pdfBytes = elhubSignedPdfBytes
            )

            val pdfValidationResult =
                signingService.validateSignaturesAndReturnSignatory(bankIdSignedPdfBytes, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.MissingNationalId)
        }

        test("Should return MissingBankIdTrustedTimestamp when bankId signature has untrusted timestamp") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()

            val bankIdSignedPdfBytes = endUserSignatureTestHelper.signWithUntrustedTimestamp(
                pdfBytes = elhubSignedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )

            val pdfValidationResult =
                signingService.validateSignaturesAndReturnSignatory(bankIdSignedPdfBytes, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.MissingBankIdTrustedTimestamp)
        }

        test("Should return ElhubSigningCertNotTrusted when Elhub signing cert isn't trusted") {
            val fakeElhubCerts = certFactory.generateFakeElhubCertificates(certProvider.getElhubSigningCertificate())
            val elhubSignedPdfBytes = TestPdfSigner.signWithChain(
                pdfBytes = unsignedPdfBytes,
                chain = fakeElhubCerts.chain,
                signingKey = fakeElhubCerts.signingKey.private,
            )
            val bankIdSignedPdfBytes = endUserSignatureTestHelper.sign(
                pdfBytes = elhubSignedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )

            val pdfValidationResult =
                signingService.validateSignaturesAndReturnSignatory(bankIdSignedPdfBytes, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.ElhubSigningCertNotTrusted)
        }

        test("Should return OriginalDocumentMismatch when signed PDF does not match stored Elhub-signed PDF") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val bankIdSignedPdfBytes = endUserSignatureTestHelper.sign(
                pdfBytes = elhubSignedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )

            val originalElhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()

            val pdfValidationResult =
                signingService.validateSignaturesAndReturnSignatory(bankIdSignedPdfBytes, originalElhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.OriginalDocumentMismatch)
        }

        test("Should return BankIdCertificateRevoked when is revoked") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val bankIdSignedPdfBytes = endUserSignatureTestHelper.signWithRevokedCertificate(
                pdfBytes = elhubSignedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber
            )

            val pdfValidationResult =
                signingService.validateSignaturesAndReturnSignatory(bankIdSignedPdfBytes, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.BankIdCertificateRevoked)
        }

        test("Should return BankIdSignatureNotPadesLT when BankId signature is Pades B-T") {
            val elhubSignedPdfBytes = signingService.sign(unsignedPdfBytes).shouldBeRight()
            val bankIdSignedPdfBytes = endUserSignatureTestHelper.sign(
                pdfBytes = elhubSignedPdfBytes,
                nationalIdentityNumber = nationalIdentityNumber,
                signingProfile = TestPdfSigner.SigningProfile.BASELINE_T
            )

            val pdfValidationResult =
                signingService.validateSignaturesAndReturnSignatory(bankIdSignedPdfBytes, elhubSignedPdfBytes)

            pdfValidationResult.shouldBeLeft(SignatureValidationError.BankIdSignatureNotPadesLT)
        }
        context("Test with BankID signed document") {
            test("Signed document from BankID test environment should be valid when trusting Elhub MT1 public key and BankID preprod public key") {

                val classLoader = this::class.java.classLoader

                val pdfBytes = classLoader.getResourceAsStream("bankid-signed-with-seal.pdf")!!.readAllBytes()

                val elhubPemFile = "elhub-public-key-mt1.pem"
                val elhubRootCertBytes = classLoader.getResourceAsStream(elhubPemFile)!!.readAllBytes()
                val tempElhubDir = Files.createTempDirectory("elhub-tmp")
                tempElhubDir.resolve(elhubPemFile).toFile().writeBytes(elhubRootCertBytes)

                val bankIdFile = "bankid-public-key-preprod.pem"
                val bankIdRootCertBytes = classLoader.getResourceAsStream(bankIdFile)!!.readAllBytes()
                val tempBankIdDir = Files.createTempDirectory("bankid-tmp")
                tempBankIdDir.resolve(bankIdFile).toFile().writeBytes(bankIdRootCertBytes)

                val tempTsaDir = Files.createTempDirectory("tsa-tmp")
                tempTsaDir.resolve(bankIdFile).toFile().writeBytes(bankIdRootCertBytes)

                val elhubCertPath = "$tempElhubDir/$elhubPemFile"
                val realCertProvider = FileCertificateProvider(
                    FileCertificateProviderConfig(
                        pathToIntermSigningCertificate = elhubCertPath,
                        pathToSigningCertificate = elhubCertPath,
                        pathToBankIdRootCertificatesDir = tempBankIdDir.toString(),
                        pathToTsaRootCertificatesDir = tempTsaDir.toString(),
                    )
                )

                val signatureService = ITextPdfSignatureService(realCertProvider, vaultSignatureProvider)
                signatureService.validateSignaturesAndReturnSignatory(pdfBytes, pdfBytes).shouldBeRight()
            }

            test("Signed document from Signicat test environment should be valid when trusting Elhub Prod public key and BankID preprod public key") {

                val classLoader = this::class.java.classLoader

                val pdfBytes = classLoader.getResourceAsStream("bankid-signed-signicat.pdf")!!.readAllBytes()

                val elhubPemFile = "elhub-public-key-prod.pem"
                val elhubRootCertBytes = classLoader.getResourceAsStream(elhubPemFile)!!.readAllBytes()
                val tempElhubDir = Files.createTempDirectory("elhub-tmp")
                tempElhubDir.resolve(elhubPemFile).toFile().writeBytes(elhubRootCertBytes)

                val bankIdFile = "bankid-public-key-preprod.pem"
                val bankIdRootCertBytes = classLoader.getResourceAsStream(bankIdFile)!!.readAllBytes()
                val tempBankIdDir = Files.createTempDirectory("bankid-tmp")
                tempBankIdDir.resolve(bankIdFile).toFile().writeBytes(bankIdRootCertBytes)

                val tsaRootFile = "buypass-class3-root-ca-g2-ht.pem"
                val tsaRootCertBytes = classLoader.getResourceAsStream(tsaRootFile)!!.readAllBytes()
                val tempTsaDir = Files.createTempDirectory("tsa-tmp")
                tempTsaDir.resolve(tsaRootFile).toFile().writeBytes(tsaRootCertBytes)

                val elhubCertPath = "$tempElhubDir/$elhubPemFile"
                val realCertProvider = FileCertificateProvider(
                    FileCertificateProviderConfig(
                        pathToIntermSigningCertificate = elhubCertPath,
                        pathToSigningCertificate = elhubCertPath,
                        pathToBankIdRootCertificatesDir = tempBankIdDir.toString(),
                        pathToTsaRootCertificatesDir = tempTsaDir.toString(),
                    )
                )

                val signatureService =
                    ITextPdfSignatureService(realCertProvider, vaultSignatureProvider)
                signatureService.validateSignaturesAndReturnSignatory(pdfBytes, pdfBytes)
                    .shouldBeRight()
            }
        }
    }
})
