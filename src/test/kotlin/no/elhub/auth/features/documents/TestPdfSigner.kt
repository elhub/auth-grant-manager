package no.elhub.auth.features.documents

import eu.europa.esig.dss.enumerations.CertificationPermission
import eu.europa.esig.dss.enumerations.DigestAlgorithm
import eu.europa.esig.dss.enumerations.SignatureLevel
import eu.europa.esig.dss.model.InMemoryDocument
import eu.europa.esig.dss.model.SignatureValue
import eu.europa.esig.dss.model.x509.CertificateToken
import eu.europa.esig.dss.pades.PAdESSignatureParameters
import eu.europa.esig.dss.pades.signature.PAdESService
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import org.apache.pdfbox.Loader
import java.io.ByteArrayOutputStream
import java.security.PrivateKey

object TestPdfSigner {
    fun signWithCertificate(
        pdfBytes: ByteArray,
        signingCert: java.security.cert.X509Certificate,
        chain: List<java.security.cert.X509Certificate>,
        signingKey: PrivateKey,
        signatureLevel: SignatureLevel = SignatureLevel.PAdES_BASELINE_B
    ): ByteArray {
        val padesService = PAdESService(CommonCertificateVerifier())
        val signatureParameters = PAdESSignatureParameters().apply {
            this.signatureLevel = signatureLevel
            digestAlgorithm = DigestAlgorithm.SHA256
            permission = CertificationPermission.MINIMAL_CHANGES_PERMITTED
            signingCertificate = CertificateToken(signingCert)
            certificateChain = chain.map(::CertificateToken)
        }

        val document = InMemoryDocument(pdfBytes)
        val dataToSign = padesService.getDataToSign(document, signatureParameters).bytes
        val signatureBytes = signData(dataToSign, signingKey, signatureParameters.signatureAlgorithm.jceId)
        val signatureValue = SignatureValue(signatureParameters.signatureAlgorithm, signatureBytes)

        return padesService.signDocument(document, signatureParameters, signatureValue)
            .openStream()
            .use { it.readBytes() }
    }

    private fun signData(data: ByteArray, key: PrivateKey, jceId: String): ByteArray {
        val signature = java.security.Signature.getInstance(jceId)
        signature.initSign(key)
        signature.update(data)
        return signature.sign()
    }

    fun tamperPdf(pdfBytes: ByteArray): ByteArray {
        ByteArrayOutputStream().use { output ->
            Loader.loadPDF(pdfBytes).use { doc ->
                // Make a visible change and reserialize to invalidate the original signature.
                doc.documentInformation.setCustomMetadataValue("tampered", "true")
                doc.save(output)
            }
            return output.toByteArray()
        }
    }

    fun tamperLatestSignature(pdfBytes: ByteArray): ByteArray {
        val signatures = Loader.loadPDF(pdfBytes).use { doc ->
            doc.signatureDictionaries
                .filter { it.byteRange != null }
                .sortedBy { it.byteRange[2] }
        }
        require(signatures.isNotEmpty()) { "No signatures found in PDF" }
        val earliestSignature = signatures.first()
        val latestSignature = signatures.last()
        val byteRange = latestSignature.byteRange ?: error("Signature ByteRange is missing")
        val earliestByteRange = earliestSignature.byteRange ?: error("Earliest signature ByteRange is missing")
        require(byteRange.size == 4) { "Signature ByteRange must have 4 entries" }
        val start = byteRange[1]
        val end = byteRange[2]
        require(start in 0..pdfBytes.size && end in 0..pdfBytes.size && start < end) {
            "Signature contents range is invalid: $start..$end"
        }
        val tampered = pdfBytes.copyOf()
        val earliestSignedEnd = maxOf(
            earliestByteRange[1],
            earliestByteRange[2] + earliestByteRange[3]
        ).coerceAtMost(tampered.size)
        val firstSegmentEnd = byteRange[1].coerceAtMost(tampered.size)
        val secondSegmentStart = byteRange[2].coerceAtMost(tampered.size)
        val secondSegmentEnd = (byteRange[2] + byteRange[3]).coerceAtMost(tampered.size)
        val tamperIndex = findWhitespaceInRange(
            tampered,
            earliestSignedEnd,
            minOf(firstSegmentEnd, tampered.size)
        ) ?: findWhitespaceInRange(
            tampered,
            maxOf(earliestSignedEnd, secondSegmentStart),
            secondSegmentEnd
        ) ?: error("No whitespace found to tamper in latest-only signed ranges")
        tampered[tamperIndex] = if (tampered[tamperIndex] == ' '.code.toByte()) {
            '\n'.code.toByte()
        } else {
            ' '.code.toByte()
        }
        return tampered
    }

    private fun findWhitespaceInRange(bytes: ByteArray, startInclusive: Int, endExclusive: Int): Int? {
        if (startInclusive >= endExclusive) return null
        for (index in startInclusive until endExclusive) {
            if (bytes[index].isPdfWhitespace()) {
                return index
            }
        }
        return null
    }

    private fun Byte.isPdfWhitespace(): Boolean = this == 0x00.toByte() ||
        this == 0x09.toByte() ||
        this == 0x0A.toByte() ||
        this == 0x0C.toByte() ||
        this == 0x0D.toByte() ||
        this == 0x20.toByte()
}
