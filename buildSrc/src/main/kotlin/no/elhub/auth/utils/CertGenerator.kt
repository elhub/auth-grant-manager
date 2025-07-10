package no.elhub.auth.utils

import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.DistributionPoint
import org.bouncycastle.asn1.x509.DistributionPointName
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.cert.X509Certificate
import java.util.*

/**
 * Generates a self-signed root certificate and private key for testing PAdES signatures.
 *
 * This function creates a root certificate authority (CA) and a corresponding private key,
 * which are saved as PEM files under `build/tmp/test-certs/`. These are intended to be used
 * in a test environment where PAdES signatures are applied to documents.
 */
fun generateSelfSignedCertificate(baseDirLocation: String) {
    Security.addProvider(BouncyCastleProvider())

    val baseDir = File(baseDirLocation)
    baseDir.mkdirs()

    val crlFile = baseDir.resolve("ca.crl")
    val crlUri = "file://${crlFile.absolutePath.replace("\\", "/")}"

    val caKeyPair = generateKeyPair()
    val caCert = generateSelfSignedCert("CN=My-Root-CA", caKeyPair, 3650, crlUri)

    writePem(baseDir.resolve("self-signed-key.pem"), caKeyPair.private)
    writePem(baseDir.resolve("self-signed-cert.pem"), caCert)

    println("PEM files and CRL written to: ${baseDir.absolutePath}")
}

fun generateKeyPair(): KeyPair =
    KeyPairGenerator.getInstance("Ed25519").genKeyPair()
fun generateSelfSignedCert(
    subjectDn: String,
    keyPair: KeyPair,
    daysValid: Int,
    crlUri: String? = null
): X509Certificate {
    val now = Date()
    val until = Date(now.time + daysValid * 24L * 60 * 60 * 1000)

    val builder = JcaX509v3CertificateBuilder(
        X500Name(subjectDn),
        BigInteger.valueOf(System.currentTimeMillis()),
        now,
        until,
        X500Name(subjectDn),
        keyPair.public
    ).addExtension(
        Extension.basicConstraints,
        true,
        BasicConstraints(true)
    )

    // Add CRL Distribution Point extension only if crlUri is not null or blank
    val withCrl = if (!crlUri.isNullOrBlank()) {
        val crlDistPoint = DistributionPoint(
            DistributionPointName(
                GeneralNames(
                    GeneralName(GeneralName.uniformResourceIdentifier, DERIA5String(crlUri))
                )
            ),
            null,
            null
        )
        builder.addExtension(
            Extension.cRLDistributionPoints,
            false,
            CRLDistPoint(arrayOf(crlDistPoint))
        )
    } else {
        builder
    }

    val signer = JcaContentSignerBuilder("Ed25519").build(keyPair.private)
    return JcaX509CertificateConverter().getCertificate(withCrl.build(signer))
}

fun writePem(file: File, obj: Any) {
    file.writer().use { writer ->
        JcaPEMWriter(writer).use { pemWriter ->
            pemWriter.writeObject(obj)
        }
    }
}
