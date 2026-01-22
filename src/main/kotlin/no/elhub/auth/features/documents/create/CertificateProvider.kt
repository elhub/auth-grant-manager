package no.elhub.auth.features.documents.create

import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

interface CertificateProvider {
    fun getElhubSigningCertificate(): X509Certificate
    fun getElhubCertificateChain(): List<X509Certificate>
    fun getBankIdRootCertificate(): X509Certificate
}

sealed class CertificateRetrievalError {
    data object IOError : CertificateRetrievalError()
}

const val CERT_TYPE = "X.509"

class FileCertificateProviderConfig(
    val pathToCertificateChain: String,
    val pathToSigningCertificate: String,
    val pathToBankIdRootCertificate: String,
)

class FileCertificateProvider(
    cfg: FileCertificateProviderConfig
) : CertificateProvider {

    private val elhubSigningCert: X509Certificate =
        readSingleCert(cfg.pathToSigningCertificate)

    private val elhubChain: List<X509Certificate> =
        readChain(cfg.pathToCertificateChain)

    private val bankIdRootCert: X509Certificate =
        readSingleCert(cfg.pathToBankIdRootCertificate)

    override fun getElhubSigningCertificate() = elhubSigningCert
    override fun getElhubCertificateChain() = elhubChain
    override fun getBankIdRootCertificate() = bankIdRootCert

    private fun readChain(path: String): List<X509Certificate> =
        File(path).inputStream().use {
            CertificateFactory.getInstance(CERT_TYPE)
                .generateCertificates(it)
                .filterIsInstance<X509Certificate>()
                .also { require(it.isNotEmpty()) { "No certs found at $path" } }
        }

    private fun readSingleCert(path: String): X509Certificate =
        readChain(path).single()
}
