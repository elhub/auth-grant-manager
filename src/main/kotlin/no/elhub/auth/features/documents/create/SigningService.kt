package no.elhub.auth.features.documents.create

import arrow.core.Either
import eu.europa.esig.dss.enumerations.CertificationPermission
import eu.europa.esig.dss.enumerations.DigestAlgorithm
import eu.europa.esig.dss.enumerations.SignatureLevel
import eu.europa.esig.dss.model.DSSDocument
import eu.europa.esig.dss.model.InMemoryDocument
import eu.europa.esig.dss.model.SignatureValue
import eu.europa.esig.dss.model.x509.CertificateToken
import eu.europa.esig.dss.pades.PAdESSignatureParameters
import eu.europa.esig.dss.pades.signature.PAdESService
import java.security.cert.X509Certificate

interface FileSigningService {
    fun getDataToSign(
        file: ByteArray,
        certChain: List<X509Certificate>,
        signingCert: X509Certificate,
    ): Either<DocumentSigningError, ByteArray>

    suspend fun embedSignatureIntoFile(
        file: ByteArray,
        signatureBytes: ByteArray,
        certChain: List<X509Certificate>,
        signingCert: X509Certificate,
    ): Either<DocumentSigningError, ByteArray>
}

sealed class DocumentSigningError {
    data object SigningDataGenerationError : DocumentSigningError()
    data object SigningError : DocumentSigningError()
}

class PAdESDocumentSigningService(
    private val padesService: PAdESService,
) : FileSigningService {

    private val defaultSignatureParameters = PAdESSignatureParameters().apply {
        signatureLevel = SignatureLevel.PAdES_BASELINE_B
        digestAlgorithm = DigestAlgorithm.SHA256
        permission = CertificationPermission.MINIMAL_CHANGES_PERMITTED
    }

    override fun getDataToSign(
        file: ByteArray,
        certChain: List<X509Certificate>,
        signingCert: X509Certificate,
    ): Either<DocumentSigningError, ByteArray> = Either.catch {
        padesService.getDataToSign(
            InMemoryDocument(file),
            defaultSignatureParameters.apply {
                certificateChain = certChain.map(::CertificateToken)
                signingCertificate = CertificateToken(signingCert)
            }
        ).bytes
    }.mapLeft { DocumentSigningError.SigningDataGenerationError }

    override suspend fun embedSignatureIntoFile(
        file: ByteArray,
        signatureBytes: ByteArray,
        certChain: List<X509Certificate>,
        signingCert: X509Certificate,
    ): Either<DocumentSigningError, ByteArray> = sign(
        InMemoryDocument(file),
        SignatureValue(defaultSignatureParameters.signatureAlgorithm, signatureBytes),
        certChain.map(::CertificateToken),
        CertificateToken(signingCert),
    )

    private fun sign(
        document: DSSDocument,
        signature: SignatureValue,
        certChain: List<CertificateToken>,
        signingCert: CertificateToken,
    ): Either<DocumentSigningError, ByteArray> = Either.catch {
        padesService.signDocument(
            document,
            defaultSignatureParameters.apply {
                certificateChain = certChain
                signingCertificate = signingCert
            },
            signature
        )
            .openStream()
            .use { it.readBytes() }
    }.mapLeft { ex -> DocumentSigningError.SigningError }
}
