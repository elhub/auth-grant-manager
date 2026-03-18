package no.elhub.auth.features.documents

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.signatures.SignatureUtil
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.apache.pdfbox.Loader
import java.io.ByteArrayInputStream
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

const val CERT_TYPE = "X509"

fun ByteArray.validateFileIsSignedByUs() {
    val expectedSigningCertificate =
        File(TestCertificateUtil.Constants.CERTIFICATE_LOCATION)
            .inputStream()
            .use {
                CertificateFactory.getInstance(CERT_TYPE)
                    .generateCertificates(it)
                    .filterIsInstance<X509Certificate>()
                    .single()
            }

    PdfDocument(PdfReader(this.inputStream())).use { pdfDocument ->
        val signatureUtil = SignatureUtil(pdfDocument)
        val signatureNames = signatureUtil.signatureNames

        signatureNames.size shouldBe 1

        val signatureName = signatureNames.single()
        signatureUtil.signatureCoversWholeDocument(signatureName) shouldBe true

        val pkcs7 = signatureUtil.readSignatureData(signatureName)
        pkcs7.verifySignatureIntegrityAndAuthenticity() shouldBe true

        val actualSigningCertificate = pkcs7.signingCertificate
        actualSigningCertificate.shouldNotBeNull()

        actualSigningCertificate.serialNumber shouldBe expectedSigningCertificate.serialNumber
        actualSigningCertificate.subjectX500Principal shouldBe expectedSigningCertificate.subjectX500Principal
        actualSigningCertificate.encoded.contentEquals(expectedSigningCertificate.encoded) shouldBe true
    }
}

fun ByteArray.getCustomMetaDataValue(key: String): String? =
    Loader.loadPDF(this).use { it.documentInformation.getCustomMetadataValue(key) }
