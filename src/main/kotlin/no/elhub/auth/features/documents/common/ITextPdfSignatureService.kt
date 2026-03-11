package no.elhub.auth.features.documents.common

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfRevisionsReader
import com.itextpdf.kernel.pdf.StampingProperties
import com.itextpdf.signatures.IExternalSignature
import com.itextpdf.signatures.ISignatureMechanismParams
import com.itextpdf.signatures.PdfPKCS7
import com.itextpdf.signatures.PdfSigner
import com.itextpdf.signatures.SignatureUtil
import kotlinx.coroutines.runBlocking
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.common.party.PartyIdentifierType
import no.elhub.auth.features.documents.create.CertificateProvider
import no.elhub.auth.features.documents.create.SignatureProvider
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.ASN1String
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.util.Date

class ITextPdfSignatureService(
    private val certificateProvider: CertificateProvider,
    private val signatureProvider: SignatureProvider,
) : SignatureService {
    companion object {
        const val NATIONAL_ID_EXTENSION_OID = "2.16.578.1.61.2.4"
    }

    override suspend fun sign(fileByteArray: ByteArray): Either<SignatureSigningError, ByteArray> = either {
        val certChain = certificateProvider.getElhubCertificateChain().toTypedArray<Certificate>()
        var signatureCallbackInvoked = false
        var signatureFetchFailed = false

        val externalSignature = object : IExternalSignature {
            override fun getDigestAlgorithmName(): String = "SHA-256"

            override fun getSignatureAlgorithmName(): String = "RSA"

            override fun getSignatureMechanismParameters(): ISignatureMechanismParams? = null

            override fun sign(message: ByteArray): ByteArray {
                signatureCallbackInvoked = true
                return runBlocking {
                    signatureProvider.fetchSignature(message).fold(
                        ifLeft = {
                            signatureFetchFailed = true
                            throw SignatureFetchException()
                        },
                        ifRight = { it }
                    )
                }
            }
        }

        val output = ByteArrayOutputStream()
        val signResult = runCatching {
            val signer = PdfSigner(
                PdfReader(fileByteArray.inputStream()),
                output,
                StampingProperties().useAppendMode()
            )
            signer.setFieldName("Signature1")
            signer.setCertificationLevel(PdfSigner.CERTIFIED_FORM_FILLING)
            signer.signDetached(
                externalSignature,
                certChain,
                null,
                null,
                null,
                0,
                PdfSigner.CryptoStandard.CADES
            )
            output.toByteArray()
        }

        signResult.fold(
            onSuccess = { it },
            onFailure = {
                when {
                    signatureFetchFailed -> raise(SignatureSigningError.SignatureFetchingError)
                    !signatureCallbackInvoked -> raise(SignatureSigningError.SigningDataGenerationError)
                    else -> raise(SignatureSigningError.AddSignatureToSignatureError)
                }
            }
        )
    }

    override fun validateSignaturesAndReturnSignatory(
        file: ByteArray,
        originalFile: ByteArray
    ): Either<SignatureValidationError, PartyIdentifier> = either {
        val parsedDocument = ensureNotNull(parseDocument(file)) { SignatureValidationError.MissingElhubSignature }

        val elhubExpectedCert = certificateProvider.getElhubSigningCertificate()

        val elhubSignature = ensureNotNull(parsedDocument.signatures.firstOrNull()) {
            SignatureValidationError.MissingElhubSignature
        }

        ensure(hasIssuerAndSerial(elhubSignature.signingCertificate, elhubExpectedCert)) {
            SignatureValidationError.MissingElhubSignature
        }

        validateElhubSignature(elhubSignature, elhubExpectedCert).bind()
        verifyNewMatchesOriginalByByteRange(elhubSignature, originalFile, file).bind()

        val expectedBankIdRoots = certificateProvider.getBankIdRootCertificates()
        val bankIdSignature = ensureNotNull(parsedDocument.signatures.getOrNull(1)) {
            SignatureValidationError.MissingBankIdSignature
        }

        ensureNoDisallowedChangesBetweenSignatures(
            elhubSignature,
            bankIdSignature,
            parsedDocument.allRevisionEofs
        ).bind()

        validateBankIdSignatureAndReturnSignatory(
            signature = bankIdSignature,
            expectedRoots = expectedBankIdRoots,
            dssCrls = parsedDocument.dssCrls
        ).bind()
    }

    private fun parseDocument(file: ByteArray): ParsedDocument? = runCatching {
        PdfDocument(PdfReader(file.inputStream())).use { document ->
            val signatureUtil = SignatureUtil(document)
            val parsedSignatures = signatureUtil.getSignatureNames().mapNotNull { fieldName ->
                parseSignature(signatureUtil, fieldName, signatureUtil.getRevision(fieldName))
            }
            val dssDictionary = document.getCatalog().pdfObject.getAsDictionary(PdfName.DSS)
            ParsedDocument(
                signatures = parsedSignatures,
                totalRevisions = signatureUtil.getTotalRevisions(),
                allRevisionEofs = PdfRevisionsReader(document.reader)
                    .getAllRevisions()
                    .map { it.eofOffset },
                dssCrls = parseDssCrls(dssDictionary)
            )
        }
    }.getOrNull()

    private fun parseDssCrls(dssDictionary: com.itextpdf.kernel.pdf.PdfDictionary?): List<X509CRL> {
        val crlArray = dssDictionary?.getAsArray(PdfName.CRLs) ?: return emptyList()
        val certFactory = CertificateFactory.getInstance("X.509")
        return crlArray
            .mapNotNull { it as? com.itextpdf.kernel.pdf.PdfStream }
            .mapNotNull { stream ->
                runCatching {
                    certFactory.generateCRL(ByteArrayInputStream(stream.bytes)) as X509CRL
                }.getOrNull()
            }
    }

    private fun parseSignature(signatureUtil: SignatureUtil, fieldName: String, revision: Int): ParsedSignature? {
        val signature = signatureUtil.getSignature(fieldName) ?: return null
        val pkcs7 = runCatching { signatureUtil.readSignatureData(fieldName) }.getOrNull() ?: return null
        val byteRange = signature.byteRange?.toLongArray()?.map(BigInteger::valueOf) ?: emptyList()
        val revisionEof = signatureUtil.extractRevision(fieldName)?.use { it.readBytes().size.toLong() } ?: 0L
        return ParsedSignature(
            fieldName = fieldName,
            revision = revision,
            revisionEof = revisionEof,
            isTimestampSignature = pkcs7.isTsp || signature.type == PdfName.DocTimeStamp,
            pkcs7 = pkcs7,
            signingCertificate = pkcs7.signingCertificate,
            certificateChain = pkcs7.signCertificateChain.filterIsInstance<X509Certificate>(),
            byteRange = byteRange
        )
    }

    private fun validateElhubSignature(
        signature: ParsedSignature,
        expectedElhubCert: X509Certificate,
    ): Either<SignatureValidationError, Unit> = either {
        ensure(
            runCatching { signature.pkcs7.verifySignatureIntegrityAndAuthenticity() }.getOrDefault(false)
        ) {
            SignatureValidationError.InvalidElhubSignature
        }

        val signingCert = signature.signingCertificate
        ensure(signingCert != null && areSameCertificate(signingCert, expectedElhubCert)) {
            SignatureValidationError.ElhubSigningCertNotTrusted
        }
    }

    private fun ensureNoDisallowedChangesBetweenSignatures(
        elhubSignature: ParsedSignature,
        bankIdSignature: ParsedSignature,
        allRevisionEofs: List<Long>,
    ): Either<SignatureValidationError, Unit> = either {
        val hasIntermediateRevision = allRevisionEofs.any { revisionEof ->
            revisionEof > elhubSignature.revisionEof && revisionEof < bankIdSignature.revisionEof
        }
        ensure(!hasIntermediateRevision) {
            SignatureValidationError.ElhubSignatureModifiedAfterSigning
        }
    }

    private fun verifyNewMatchesOriginalByByteRange(
        signature: ParsedSignature,
        originalElhubSignedPdf: ByteArray,
        newPdf: ByteArray
    ): Either<SignatureValidationError, Unit> = either {
        val byteRange = signature.byteRange
        ensure(byteRange.size == 4) { SignatureValidationError.OriginalDocumentMismatch }

        val digestAlgorithm = signature.pkcs7.getDigestAlgorithmName()
        val originalDigest = runCatching {
            digestOverByteRange(originalElhubSignedPdf, byteRange, digestAlgorithm)
        }.getOrNull()
        val newDigest = runCatching {
            digestOverByteRange(newPdf, byteRange, digestAlgorithm)
        }.getOrNull()

        ensure(originalDigest != null && newDigest != null && originalDigest.contentEquals(newDigest)) {
            SignatureValidationError.OriginalDocumentMismatch
        }
    }

    private fun validateBankIdSignatureAndReturnSignatory(
        signature: ParsedSignature,
        expectedRoots: List<X509Certificate>,
        dssCrls: List<X509CRL>
    ): Either<SignatureValidationError, PartyIdentifier> = either {
        val signingCert = ensureNotNull(signature.signingCertificate) {
            SignatureValidationError.InvalidBankIdSignature
        }

        ensure(isIssuedByExpectedRoot(signingCert, signature.certificateChain, expectedRoots)) {
            SignatureValidationError.BankIdSigningCertNotFromExpectedRoot
        }

        ensure(
            runCatching { signature.pkcs7.verifySignatureIntegrityAndAuthenticity() }.getOrDefault(false)
        ) {
            SignatureValidationError.InvalidBankIdSignature
        }

        val trustedTimestampTime = ensureNotNull(findTrustedTimestampTime(signature.pkcs7, expectedRoots)) {
            SignatureValidationError.MissingBankIdTrustedTimestamp
        }

        ensure(!trustedTimestampTime.before(signingCert.notBefore) && !trustedTimestampTime.after(signingCert.notAfter)) {
            SignatureValidationError.BankIdSigningCertNotValidAtTimestamp
        }

        ensureNoRevokedCertificateAt(
            timestampTime = trustedTimestampTime,
            certificateChain = signature.certificateChain,
            pkcs7 = signature.pkcs7,
            dssCrls = dssCrls
        )

        val nationalIdentityNumber = ensureNotNull(decodeNationalIdentityNumber(signingCert)) {
            SignatureValidationError.MissingNationalId
        }

        PartyIdentifier(
            idType = PartyIdentifierType.NationalIdentityNumber,
            idValue = nationalIdentityNumber
        )
    }

    private fun findTrustedTimestampTime(
        signature: PdfPKCS7,
        expectedRoots: List<X509Certificate>
    ): Date? {
        if (signature.timeStampTokenInfo == null) return null
        if (!runCatching { signature.verifyTimestampImprint() }.getOrDefault(false)) return null

        val timestampSignature = signature.timestampSignatureContainer ?: return null
        if (!runCatching { timestampSignature.verifySignatureIntegrityAndAuthenticity() }.getOrDefault(false)) {
            return null
        }

        val timestampChain = timestampSignature.signCertificateChain.filterIsInstance<X509Certificate>()
        if (timestampChain.isEmpty()) return null
        val topOfTsaChain = timestampChain.lastOrNull() ?: return null
        val trustedRootMatch =
            timestampChain.any { cert -> expectedRoots.any { root -> areSameCertificate(cert, root) } } ||
                expectedRoots.any { root ->
                    runCatching {
                        topOfTsaChain.verify(root.publicKey)
                        true
                    }.getOrDefault(
                        false
                    )
                }
        val chainIntact = isCertificateChainIntact(timestampChain)
        if (!trustedRootMatch) return null
        if (!chainIntact) return null

        return runCatching { signature.timeStampDate.time }.getOrNull()
    }

    private fun Raise<SignatureValidationError>.ensureNoRevokedCertificateAt(
        timestampTime: Date,
        certificateChain: List<X509Certificate>,
        pkcs7: PdfPKCS7,
        dssCrls: List<X509CRL>
    ) {
        val revocations = buildList<X509CRL> {
            addAll(pkcs7.crLs.orEmpty().filterIsInstance<X509CRL>())
            addAll(pkcs7.signedDataCRLs.filterIsInstance<X509CRL>())
            addAll(dssCrls)
        }

        ensure(revocations.any()) {
            SignatureValidationError.BankIdSignatureNotPadesLT
        }

        val revokedCertificate = certificateChain.firstOrNull { cert ->
            revocations.any { crl ->
                val revoked = crl.getRevokedCertificate(cert.serialNumber) ?: return@any false
                revoked.revocationDate == null || !revoked.revocationDate.after(timestampTime)
            }
        }

        ensure(revokedCertificate == null) {
            SignatureValidationError.BankIdCertificateRevoked
        }
    }

    private fun decodeNationalIdentityNumber(certificate: X509Certificate): String? {
        val extensionValue = certificate.getExtensionValue(NATIONAL_ID_EXTENSION_OID) ?: return null
        val inner = runCatching { ASN1OctetString.getInstance(extensionValue).octets }.getOrNull() ?: return null
        val asn1 = runCatching { ASN1Primitive.fromByteArray(inner) }.getOrNull() ?: return null
        return (asn1 as? ASN1String)?.string
    }

    private fun hasIssuerAndSerial(cert: X509Certificate?, expected: X509Certificate): Boolean {
        if (cert == null) return false
        return cert.issuerX500Principal.name == expected.issuerX500Principal.name &&
            cert.serialNumber == expected.serialNumber
    }

    private fun hasIssuerAndSerialAny(cert: X509Certificate?, expected: List<X509Certificate>): Boolean =
        expected.any { hasIssuerAndSerial(cert, it) }

    private fun isIssuedByExpectedRoot(
        signingCert: X509Certificate,
        chain: List<X509Certificate>,
        expectedRoots: List<X509Certificate>
    ): Boolean {
        val intermediateCert = chain.lastOrNull() ?: signingCert
        return expectedRoots.any { root ->
            runCatching {
                intermediateCert.verify(root.publicKey)
                true
            }.getOrDefault(false)
        }
    }

    private fun areSameCertificate(left: X509Certificate, right: X509Certificate): Boolean {
        val leftSpki = left.publicKey.encoded
        val rightSpki = right.publicKey.encoded
        return leftSpki.contentEquals(rightSpki) &&
            left.subjectX500Principal == right.subjectX500Principal
    }

    private fun isCertificateChainIntact(chain: List<X509Certificate>): Boolean {
        for (index in 0 until chain.lastIndex) {
            if (runCatching { chain[index].verify(chain[index + 1].publicKey) }.isFailure) {
                return false
            }
        }
        return true
    }

    private fun digestOverByteRange(
        pdf: ByteArray,
        byteRange: List<BigInteger>,
        digestAlgorithm: String
    ): ByteArray {
        val md = messageDigest(digestAlgorithm)

        val firstStart = byteRange[0].toInt()
        val firstLen = byteRange[1].toInt()
        val secondStart = byteRange[2].toInt()
        val secondLen = byteRange[3].toInt()

        require(firstStart >= 0 && firstLen >= 0 && secondStart >= 0 && secondLen >= 0)
        require(firstStart + firstLen <= pdf.size)
        require(secondStart + secondLen <= pdf.size)

        md.update(pdf, firstStart, firstLen)
        md.update(pdf, secondStart, secondLen)
        return md.digest()
    }

    private fun messageDigest(algorithm: String): MessageDigest {
        val normalized = when (algorithm.uppercase()) {
            "SHA256", "SHA-256" -> "SHA-256"
            "SHA384", "SHA-384" -> "SHA-384"
            "SHA512", "SHA-512" -> "SHA-512"
            else -> algorithm
        }
        return MessageDigest.getInstance(normalized)
    }

    private class SignatureFetchException : GeneralSecurityException()

    private data class ParsedDocument(
        val signatures: List<ParsedSignature>,
        val totalRevisions: Int,
        val allRevisionEofs: List<Long>,
        val dssCrls: List<X509CRL>,
    )

    private data class ParsedSignature(
        val fieldName: String,
        val revision: Int,
        val revisionEof: Long,
        val isTimestampSignature: Boolean,
        val pkcs7: PdfPKCS7,
        val signingCertificate: X509Certificate?,
        val certificateChain: List<X509Certificate>,
        val byteRange: List<BigInteger>,
    )
}
