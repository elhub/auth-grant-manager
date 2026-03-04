package no.elhub.auth.features.documents

import eu.europa.esig.dss.alert.SilentOnStatusAlert
import eu.europa.esig.dss.crl.CRLBinary
import eu.europa.esig.dss.enumerations.CertificationPermission
import eu.europa.esig.dss.enumerations.DigestAlgorithm
import eu.europa.esig.dss.enumerations.RevocationOrigin
import eu.europa.esig.dss.enumerations.SignatureLevel
import eu.europa.esig.dss.model.InMemoryDocument
import eu.europa.esig.dss.model.SignatureValue
import eu.europa.esig.dss.model.x509.CertificateToken
import eu.europa.esig.dss.model.x509.revocation.crl.CRL
import eu.europa.esig.dss.pades.PAdESSignatureParameters
import eu.europa.esig.dss.pades.signature.PAdESService
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource
import eu.europa.esig.dss.spi.x509.revocation.RevocationToken
import eu.europa.esig.dss.spi.x509.revocation.crl.OfflineCRLSource
import eu.europa.esig.dss.spi.x509.tsp.TSPSource
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import java.io.ByteArrayOutputStream
import java.security.PrivateKey
import java.security.cert.X509CRL

object TestPdfSigner {
    fun signWithCertificate(
        pdfBytes: ByteArray,
        signingCert: java.security.cert.X509Certificate,
        chain: List<java.security.cert.X509Certificate>,
        signingKey: PrivateKey,
        signatureLevel: SignatureLevel,
        tspSource: TSPSource? = null,
        crls: List<X509CRL> = emptyList()
    ): ByteArray {
        if ((signatureLevel == SignatureLevel.PAdES_BASELINE_LT || signatureLevel == SignatureLevel.PAdES_BASELINE_LTA) && crls.isEmpty()) {
            error("CRL data must be provided for PAdES LT/LTA signatures in tests")
        }

        val trustedSource = CommonTrustedCertificateSource().apply {
            addCertificate(CertificateToken(chain.last()))
        }

        val verifier = CommonCertificateVerifier().apply {
            setTrustedCertSources(trustedSource)
            alertOnRevokedCertificate = SilentOnStatusAlert()
            alertOnMissingRevocationData = SilentOnStatusAlert()
            alertOnNoRevocationAfterBestSignatureTime = SilentOnStatusAlert()
            alertOnRevokedCertificate = SilentOnStatusAlert()
            alertOnInvalidSignature = SilentOnStatusAlert()
            if (crls.isNotEmpty()) {
                crlSource = StaticCrlSource(crls)
            }
        }
        val padesService = PAdESService(verifier)
        if (tspSource != null) {
            padesService.setTspSource(tspSource)
        }
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

    private class StaticCrlSource(crls: List<X509CRL>) : OfflineCRLSource() {
        init {
            crls.forEach { crl ->
                addBinary(CRLBinary(crl.encoded), RevocationOrigin.EXTERNAL)
            }
        }

        override fun getRevocationTokens(
            certificateToken: CertificateToken,
            issuerToken: CertificateToken
        ): List<RevocationToken<CRL>> {
            val tokens = super.getRevocationTokens(certificateToken, issuerToken)
            tokens.forEach { it.setExternalOrigin(RevocationOrigin.EXTERNAL) }
            return tokens
        }
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

    fun addAnnotationIncremental(pdfBytes: ByteArray): ByteArray {
        ByteArrayOutputStream().use { output ->
            Loader.loadPDF(pdfBytes).use { doc ->
                val page = doc.getPage(0)
                val annotation = PDAnnotationText().apply {
                    contents = "Test annotation"
                    rectangle = PDRectangle(50f, 50f, 200f, 50f)
                }
                val annotations = page.annotations
                annotations.add(annotation)
                page.annotations = annotations
                doc.saveIncremental(output)
            }
            return output.toByteArray()
        }
    }

    fun addPageIncremental(pdfBytes: ByteArray): ByteArray {
        ByteArrayOutputStream().use { output ->
            Loader.loadPDF(pdfBytes).use { doc ->
                doc.addPage(PDPage())
                doc.saveIncremental(output)
            }
            return output.toByteArray()
        }
    }

    fun addVisualChangeIncremental(pdfBytes: ByteArray): ByteArray {
        ByteArrayOutputStream().use { output ->
            Loader.loadPDF(pdfBytes).use { doc ->
                val page = doc.getPage(0)
                PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { contentStream ->
                    contentStream.beginText()
                    contentStream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12f)
                    contentStream.newLineAtOffset(72f, 72f)
                    contentStream.showText("visual-change")
                    contentStream.endText()
                }
                doc.saveIncremental(output)
            }
            return output.toByteArray()
        }
    }
}
