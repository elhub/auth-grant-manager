package no.elhub.auth.features.documents

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.StampingProperties
import com.itextpdf.signatures.AccessPermissions
import com.itextpdf.signatures.CrlClientOffline
import com.itextpdf.signatures.PdfSigner
import com.itextpdf.signatures.PrivateKeySignature
import com.itextpdf.signatures.SignatureUtil
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import java.io.ByteArrayOutputStream
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509CRL
import java.security.cert.X509Certificate

object TestPdfSigner {
    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    enum class SigningProfile {
        BASELINE_B,
        BASELINE_T,
        BASELINE_LT
    }

    fun signWithChain(
        pdfBytes: ByteArray,
        chain: List<X509Certificate>,
        signingKey: PrivateKey,
        signingProfile: SigningProfile = SigningProfile.BASELINE_LT,
        tsaConfig: TsaConfig? = null,
        crls: List<X509CRL> = emptyList()
    ): ByteArray = ByteArrayOutputStream().use { output ->
        val externalSignature = PrivateKeySignature(signingKey, "SHA-256", "BC")
        val nextFieldName = nextSignatureFieldName(pdfBytes)
        val signer = PdfSigner(
            PdfReader(pdfBytes.inputStream()),
            output,
            StampingProperties().useAppendMode()
        )
        val tsaClient = if (signingProfile == SigningProfile.BASELINE_B) {
            null
        } else {
            tsaConfig?.let(::InMemoryTsaClient)
        }
        val crlClients = if (signingProfile == SigningProfile.BASELINE_LT) {
            crls.map(::CrlClientOffline).takeIf { it.isNotEmpty() }
        } else {
            null
        }

        signer.signerProperties.setFieldName(nextFieldName)
        signer.signerProperties.setCertificationLevel(AccessPermissions.FORM_FIELDS_MODIFICATION)

        signer.signDetached(
            externalSignature,
            chain.toTypedArray(),
            crlClients,
            null,
            tsaClient,
            0,
            PdfSigner.CryptoStandard.CADES
        )

        output.toByteArray()
    }

    private fun nextSignatureFieldName(pdfBytes: ByteArray): String {
        val existingNames = PdfDocument(PdfReader(pdfBytes.inputStream())).use { document ->
            SignatureUtil(document).signatureNames.toSet()
        }
        return generateSequence(1) { it + 1 }
            .map { "Signature$it" }
            .first { it !in existingNames }
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
