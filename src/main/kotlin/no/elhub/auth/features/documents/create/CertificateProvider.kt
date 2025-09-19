package no.elhub.auth.features.documents.create

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.catch
import arrow.core.right
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

interface CertificateProvider {
    fun getCertificate(): Either<CertificateRetrievalError, X509Certificate>
    fun getCertificateChain(): Either<CertificateRetrievalError, List<X509Certificate>>
}

sealed class CertificateRetrievalError {
    data object IOError : CertificateRetrievalError()
}

const val CERT_TYPE = "X.509"

class FileCertificateProviderConfig(
    val pathToCertificateChain: String,
    val pathToSigningCertificate: String,
)

class FileCertificateProvider(
    private val cfg: FileCertificateProviderConfig
) : CertificateProvider {

    private fun getCertificateChain(path: String): Either<CertificateRetrievalError, List<X509Certificate>> =
        Either.catch {
            File(path)
                .inputStream()
                .use {
                    CertificateFactory
                        .getInstance(CERT_TYPE)
                        .generateCertificates(it)
                        .filterIsInstance<X509Certificate>()
                }
        }.mapLeft { CertificateRetrievalError.IOError }

    override fun getCertificate(): Either<CertificateRetrievalError, X509Certificate> =
        getCertificateChain(cfg.pathToSigningCertificate)
            .fold(
                { error -> error.left() },
                { certificates -> certificates.single().right() }
            )

    override fun getCertificateChain() = getCertificateChain(cfg.pathToCertificateChain)
}
