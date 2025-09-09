package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.getOrElse
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

typealias SigningCertificate = X509Certificate
typealias SigningCertificateChain = List<X509Certificate>

interface DocumentSigningService {
    suspend fun sign(pdfByteArray: ByteArray): Either<DocumentSigningError, ByteArray>
}

sealed class DocumentSigningError {
    data object SigningDataGenerationError : DocumentSigningError()
    data object SigningError : DocumentSigningError()
}

class PAdESDocumentSigningService(
    private val vaultProvider: SignatureProvider,
    private val certificate: SigningCertificate,
    private val chain: SigningCertificateChain,
    private val padesService: PAdESService,
) : DocumentSigningService {

    private val defaultSignatureParameters = PAdESSignatureParameters().apply {
        signatureLevel = SignatureLevel.PAdES_BASELINE_B
        digestAlgorithm = DigestAlgorithm.SHA256
        permission = CertificationPermission.MINIMAL_CHANGES_PERMITTED
        signingCertificate = CertificateToken(certificate)
        certificateChain = chain.map(::CertificateToken)
    }

    private fun getDataToSign(pdfByteArray: ByteArray): Either<DocumentSigningError, ByteArray> = Either.catch {
        padesService.getDataToSign(InMemoryDocument(pdfByteArray), defaultSignatureParameters).bytes
    }.mapLeft { DocumentSigningError.SigningDataGenerationError }

    override suspend fun sign(
        pdfByteArray: ByteArray,
    ): Either<DocumentSigningError, ByteArray> {

        val dataToSign = getDataToSign(pdfByteArray)
            .getOrElse { return Either.Left(it) }

        val signature = vaultProvider.fetchSignature(dataToSign)
            .getOrElse { return Either.Left(DocumentSigningError.SigningError) }

        return  sign(
            InMemoryDocument(pdfByteArray),
            SignatureValue(defaultSignatureParameters.signatureAlgorithm, signature),
        )
    }

    private fun sign(
        document: DSSDocument,
        signature: SignatureValue
    ): Either<DocumentSigningError, ByteArray> = Either.catch {
        padesService.signDocument(
            document,
            defaultSignatureParameters,
            signature
        )
            .openStream()
            .use { it.readBytes() }
    }.mapLeft { ex ->
        print(ex)
        DocumentSigningError.SigningError
    }
}
