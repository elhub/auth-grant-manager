package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.raise.either
import eu.europa.esig.dss.enumerations.CertificationPermission
import eu.europa.esig.dss.enumerations.DigestAlgorithm
import eu.europa.esig.dss.enumerations.SignatureLevel
import eu.europa.esig.dss.model.InMemoryDocument
import eu.europa.esig.dss.model.SignatureValue
import eu.europa.esig.dss.model.x509.CertificateToken
import eu.europa.esig.dss.pades.PAdESSignatureParameters
import eu.europa.esig.dss.pades.signature.PAdESService

interface SigningService {
    suspend fun sign(fileByteArray: ByteArray): Either<FileSigningError, ByteArray>
}

sealed class FileSigningError {
    data object SigningDataGenerationError : FileSigningError()
    data object AddSignatureToFileError : FileSigningError()
    data object CertificateRetrievalError : FileSigningError()
    data object SignatureFetchingError : FileSigningError()
}

class PdfSigningService(
    private val padesService: PAdESService,
    private val certificateProvider: CertificateProvider,
    private val signatureProvider: SignatureProvider,
) : SigningService {

    override suspend fun sign(fileByteArray: ByteArray): Either<FileSigningError, ByteArray> = either {
        val certChain =
            certificateProvider
                .getCertificateChain()
                .mapLeft { FileSigningError.CertificateRetrievalError }.bind()

        val signingCert =
            certificateProvider
                .getCertificate()
                .mapLeft { FileSigningError.CertificateRetrievalError }.bind()

        val signatureParameters = PAdESSignatureParameters().apply {
            signatureLevel = SignatureLevel.PAdES_BASELINE_B
            digestAlgorithm = DigestAlgorithm.SHA256
            permission = CertificationPermission.MINIMAL_CHANGES_PERMITTED
            certificateChain = certChain.map(::CertificateToken)
            signingCertificate = CertificateToken(signingCert)
        }

        val file = InMemoryDocument(fileByteArray)

        val dataToSign = Either.catch { padesService.getDataToSign(file, signatureParameters).bytes }
            .mapLeft { FileSigningError.SigningDataGenerationError }.bind()

        val signatureBytes =
            signatureProvider
                .fetchSignature(dataToSign)
                .mapLeft { FileSigningError.SignatureFetchingError }.bind()

        val signatureValue = SignatureValue(signatureParameters.signatureAlgorithm, signatureBytes)

        Either.catch {
            padesService.signDocument(file, signatureParameters, signatureValue)
                .openStream()
                .use { it.readBytes() }
        }.mapLeft { FileSigningError.AddSignatureToFileError }.bind()
    }
}
