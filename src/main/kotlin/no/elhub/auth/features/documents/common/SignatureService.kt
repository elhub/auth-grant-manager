package no.elhub.auth.features.documents.common

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import eu.europa.esig.dss.diagnostic.CertificateWrapper
import eu.europa.esig.dss.diagnostic.SignatureWrapper
import eu.europa.esig.dss.diagnostic.jaxb.XmlCertificateExtension
import eu.europa.esig.dss.enumerations.CertificationPermission
import eu.europa.esig.dss.enumerations.DigestAlgorithm
import eu.europa.esig.dss.enumerations.SignatureLevel
import eu.europa.esig.dss.model.InMemoryDocument
import eu.europa.esig.dss.model.SignatureValue
import eu.europa.esig.dss.model.x509.CertificateToken
import eu.europa.esig.dss.pades.PAdESSignatureParameters
import eu.europa.esig.dss.pades.signature.PAdESService
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource
import eu.europa.esig.dss.validation.SignedDocumentValidator
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.documents.create.CertificateProvider
import no.elhub.auth.features.documents.create.SignatureProvider
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.ASN1String
import java.math.BigInteger
import java.security.cert.X509Certificate

interface SignatureService {
    suspend fun sign(fileByteArray: ByteArray): Either<SignatureSigningError, ByteArray>

    fun validateSignaturesAndReturnSignatory(
        file: ByteArray,
        originalFile: ByteArray,
    ): Either<SignatureValidationError, PartyIdentifier>
}

sealed class SignatureSigningError {
    data object SigningDataGenerationError : SignatureSigningError()

    data object AddSignatureToSignatureError : SignatureSigningError()

    data object SignatureFetchingError : SignatureSigningError()
}

sealed class SignatureValidationError {
    data object MissingElhubSignature : SignatureValidationError()

    data object InvalidElhubSignature : SignatureValidationError()

    data object ElhubSigningCertNotTrusted : SignatureValidationError()

    data object MissingBankIdSignature : SignatureValidationError()

    data object InvalidBankIdSignature : SignatureValidationError()

    data object BankIdSigningCertNotFromExpectedRoot : SignatureValidationError()

    data object MissingNationalId : SignatureValidationError()

    data object OriginalDocumentMismatch : SignatureValidationError()
}

class PdfSignatureService(
    private val padesService: PAdESService,
    private val certificateProvider: CertificateProvider,
    private val signatureProvider: SignatureProvider,
) : SignatureService {
    companion object {
        const val NATIONAL_ID_EXTENSION_OID = "2.16.578.1.61.2.4"
    }

    private val trustedSource =
        CommonTrustedCertificateSource().apply {
            addCertificate(CertificateToken(certificateProvider.getElhubSigningCertificate()))
            addCertificate(CertificateToken(certificateProvider.getBankIdRootCertificate()))
        }

    private val verifier =
        CommonCertificateVerifier(true).apply {
            setTrustedCertSources(trustedSource)
            isCheckRevocationForUntrustedChains = false
            ocspSource = null
            crlSource = null
        }

    override suspend fun sign(fileByteArray: ByteArray): Either<SignatureSigningError, ByteArray> =
        either {
            val certChain =
                certificateProvider
                    .getElhubCertificateChain()

            val signingCert =
                certificateProvider
                    .getElhubSigningCertificate()

            val signatureParameters =
                PAdESSignatureParameters().apply {
                    signatureLevel = SignatureLevel.PAdES_BASELINE_B
                    digestAlgorithm = DigestAlgorithm.SHA256
                    permission = CertificationPermission.MINIMAL_CHANGES_PERMITTED
                    certificateChain = certChain.map(::CertificateToken)
                    signingCertificate = CertificateToken(signingCert)
                }

            val file = InMemoryDocument(fileByteArray)

            val dataToSign =
                Either
                    .catch { padesService.getDataToSign(file, signatureParameters).bytes }
                    .mapLeft { SignatureSigningError.SigningDataGenerationError }
                    .bind()

            val signatureBytes =
                signatureProvider
                    .fetchSignature(dataToSign)
                    .mapLeft { SignatureSigningError.SignatureFetchingError }
                    .bind()

            val signatureValue = SignatureValue(signatureParameters.signatureAlgorithm, signatureBytes)

            Either
                .catch {
                    padesService
                        .signDocument(file, signatureParameters, signatureValue)
                        .openStream()
                        .use { it.readBytes() }
                }.mapLeft { SignatureSigningError.AddSignatureToSignatureError }
                .bind()
        }

    override fun validateSignaturesAndReturnSignatory(
        file: ByteArray,
        originalFile: ByteArray,
    ): Either<SignatureValidationError, PartyIdentifier> =
        either {
            val document = InMemoryDocument(file)

            val validator =
                SignedDocumentValidator.fromDocument(document).apply {
                    setCertificateVerifier(verifier)
                }

            val reports = validator.validateDocument()
            val simpleReport = reports.simpleReport
            val signatureIds = simpleReport.signatureIdList

            val diagnosticData = reports.diagnosticData

            val signatures = signatureIds.map(diagnosticData::getSignatureById)

            val elhubSignature =
                ensureNotNull(
                    signatures.firstOrNull { signature ->
                        hasIssuerAndSerial(signature.signingCertificate, certificateProvider.getElhubSigningCertificate())
                    },
                ) {
                    SignatureValidationError.MissingElhubSignature
                }
            validateElhubSignature(elhubSignature).bind()
            verifyNewMatchesOriginalByByteRange(elhubSignature, originalFile, file).bind()

            val bankIdSignature =
                ensureNotNull(
                    signatures.firstOrNull { signature ->
                        val rootCert = signature.certificateChain.lastOrNull()
                        hasIssuerAndSerial(rootCert, certificateProvider.getBankIdRootCertificate())
                    },
                ) {
                    SignatureValidationError.MissingBankIdSignature
                }
            validateBankIdSignatureAndReturnSignatory(bankIdSignature).bind()
        }

    private fun verifyNewMatchesOriginalByByteRange(
        signature: SignatureWrapper,
        originalElhubSignedPdf: ByteArray,
        newPdf: ByteArray,
    ): Either<SignatureValidationError, Unit> =
        either {
            val byteRange = signature.signatureByteRange
            ensure(byteRange.size == 4) { SignatureValidationError.OriginalDocumentMismatch }

            val algo =
                signature.digestAlgorithm
                    ?: raise(SignatureValidationError.OriginalDocumentMismatch)

            val originalDigest = digestOverByteRange(originalElhubSignedPdf, byteRange, algo)
            val newDigest = digestOverByteRange(newPdf, byteRange, algo)

            ensure(originalDigest.contentEquals(newDigest)) {
                SignatureValidationError.OriginalDocumentMismatch
            }
        }

    private fun validateElhubSignature(signature: SignatureWrapper): Either<SignatureValidationError, Unit> =
        either {
            val isSignatureIntact = signature.isSignatureIntact
            val isSignatureValid = signature.isSignatureValid

            ensure(isSignatureValid && isSignatureIntact) {
                SignatureValidationError.InvalidElhubSignature
            }

            val signingCert = signature.signingCertificate
            ensure(signingCert != null && signingCert.isTrusted) {
                SignatureValidationError.ElhubSigningCertNotTrusted
            }
        }

    private fun validateBankIdSignatureAndReturnSignatory(signature: SignatureWrapper): Either<SignatureValidationError, PartyIdentifier> =
        either {
            ensure(signature.isSignatureIntact && signature.isSignatureValid) {
                SignatureValidationError.InvalidBankIdSignature
            }

            val rootCert = signature.certificateChain.lastOrNull()

            ensure(rootCert != null && rootCert.isTrusted) {
                SignatureValidationError.BankIdSigningCertNotFromExpectedRoot
            }
            val signingCert = signature.signingCertificate

            ensure(signingCert != null) {
                SignatureValidationError.InvalidBankIdSignature
            }

            val nationalIdExtension =
                ensureNotNull(
                    signingCert.getCertificateExtensionForOid(
                        NATIONAL_ID_EXTENSION_OID,
                        XmlCertificateExtension::class.java,
                    ),
                ) {
                    SignatureValidationError.MissingNationalId
                }

            val nationalIdentityNumber =
                ensureNotNull(decodeNationalIdentityNumber(nationalIdExtension)) {
                    SignatureValidationError.MissingNationalId
                }

            PartyIdentifier(idType = PartyIdentifierType.NationalIdentityNumber, idValue = nationalIdentityNumber)
        }

    private fun decodeNationalIdentityNumber(extension: XmlCertificateExtension): String? {
        val octets = extension.octets ?: return null
        val inner = runCatching { ASN1OctetString.getInstance(octets).octets }.getOrNull()
        val valueBytes = inner ?: octets
        val asn1 = ASN1Primitive.fromByteArray(valueBytes)
        return (asn1 as? ASN1String)?.string
    }

    private fun hasIssuerAndSerial(
        cert: CertificateWrapper?,
        expected: X509Certificate,
    ): Boolean {
        if (cert == null) return false
        return expected.issuerX500Principal.name == cert.certificateIssuerDN &&
            expected.serialNumber.toString() == cert.serialNumber
    }

    private fun digestOverByteRange(
        pdf: ByteArray,
        byteRange: List<BigInteger>,
        digestAlgorithm: DigestAlgorithm,
    ): ByteArray {
        val md = digestAlgorithm.messageDigest

        val firstStart = byteRange[0].toInt()
        val firstLen = byteRange[1].toInt()
        val secondStart = byteRange[2].toInt()
        val secondLen = byteRange[3].toInt()

        md.update(pdf, firstStart, firstLen)
        md.update(pdf, secondStart, secondLen)
        return md.digest()
    }
}
