package no.elhub.auth.grantmanager.data.services

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
import no.elhub.auth.grantmanager.domain.models.SignableDocument
import no.elhub.auth.grantmanager.domain.services.DocumentSigningService
import no.elhub.auth.grantmanager.domain.services.ExternalSigningService
import java.security.cert.X509Certificate

typealias SigningCertificate = X509Certificate
typealias SigningCertificateChain = List<X509Certificate>

class PAdESDocumentSigningService(
    private val externalSigningService: ExternalSigningService,
    private val certificate: SigningCertificate,
    private val chain: SigningCertificateChain,
) : DocumentSigningService {

    private val padesService: PAdESService = PAdESService(CommonCertificateVerifier())
    private val defaultSignatureParameters = PAdESSignatureParameters().apply {
        signatureLevel = SignatureLevel.PAdES_BASELINE_B
        digestAlgorithm = DigestAlgorithm.SHA256
        permission = CertificationPermission.MINIMAL_CHANGES_PERMITTED
        signingCertificate = CertificateToken(certificate)
        certificateChain = chain.map(::CertificateToken)
    }

    override suspend fun sign(document: SignableDocument): SignableDocument = sign(InMemoryDocument(document.bytes)).openStream().use {
        SignableDocument(document.id, document.title, it.readBytes())
    }

    suspend fun sign(document: DSSDocument): DSSDocument {

        val toBeSigned = padesService.getDataToSign(document, defaultSignatureParameters)

        val rawSignature = externalSigningService.sign(toBeSigned.bytes)

        return padesService.signDocument(document, defaultSignatureParameters, SignatureValue(defaultSignatureParameters.signatureAlgorithm, rawSignature))
    }
}
