package no.elhub.auth.features.documents

import eu.europa.esig.dss.enumerations.CertificationPermission
import eu.europa.esig.dss.enumerations.DigestAlgorithm
import eu.europa.esig.dss.enumerations.SignatureLevel
import eu.europa.esig.dss.model.DSSDocument
import eu.europa.esig.dss.model.InMemoryDocument
import eu.europa.esig.dss.model.SignatureValue
import eu.europa.esig.dss.model.x509.CertificateToken
import eu.europa.esig.dss.pades.PAdESSignatureParameters
import eu.europa.esig.dss.pades.signature.PAdESService
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import kotlinx.coroutines.runBlocking
import no.elhub.auth.config.SigningCertificate
import no.elhub.auth.config.SigningCertificateChain
import no.elhub.auth.providers.vault.VaultSignatureProvider

class SigningService(
    private val vaultProvider: VaultSignatureProvider,
    private val certificate: SigningCertificate,
    private val chain: SigningCertificateChain,
    private val padesService: PAdESService = PAdESService(CommonCertificateVerifier())

) {

    fun addPadesSignature(pdfByteArray: ByteArray): ByteArray {
        val toSignDoc: DSSDocument = InMemoryDocument(pdfByteArray)

        val params = PAdESSignatureParameters().apply {
            signatureLevel = SignatureLevel.PAdES_BASELINE_B
            digestAlgorithm = DigestAlgorithm.SHA256
            permission = CertificationPermission.MINIMAL_CHANGES_PERMITTED
            signingCertificate = CertificateToken(certificate)
            certificateChain = chain.map(::CertificateToken)
        }

        val toBeSigned = padesService.getDataToSign(toSignDoc, params)

        val rawSignature = runBlocking { vaultProvider.sign(toBeSigned.bytes) }

        val signatureValue = SignatureValue(params.signatureAlgorithm, rawSignature)

        val signedDoc = padesService.signDocument(toSignDoc, params, signatureValue)

        return signedDoc.openStream().use { it.readBytes() }
    }
}
