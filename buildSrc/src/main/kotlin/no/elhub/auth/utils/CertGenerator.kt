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
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date

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
    val elhubDir = baseDir.resolve("elhub").apply { mkdirs() }
    val bankIdCertsDir = baseDir.resolve("bankid/certs").apply { mkdirs() }
    val bankIdKeysDir = baseDir.resolve("bankid/keys").apply { mkdirs() }

    val crlFile = baseDir.resolve("ca.crl")
    val crlUri = "file://${crlFile.absolutePath.replace("\\", "/")}"

    val caKeyPair = generateKeyPair()
    val caCert = generateSelfSignedCert("CN=My-Root-CA", caKeyPair, 3650, crlUri)

    writePem(elhubDir.resolve("self-signed-key.pem"), caKeyPair.private)
    writePem(elhubDir.resolve("self-signed-cert.pem"), caCert)

    val bankIdKeyPair = generateKeyPair()
    val today = LocalDate.now(ZoneOffset.UTC)
    val bankIdCert = generateSelfSignedCertWithDates(
        subjectDn = "CN=Test BankID Root",
        keyPair = bankIdKeyPair,
        notBefore = today.minusYears(1),
        notAfter = today.plusYears(1),
        crlUri = crlUri
    )

    writePem(bankIdKeysDir.resolve("bankid-root-key.pem"), bankIdKeyPair.private)
    writePem(bankIdCertsDir.resolve("bankid-root-cert.pem"), bankIdCert)
}

fun generateKeyPair(): KeyPair =
    KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()

fun generateSelfSignedCert(
    subjectDn: String,
    keyPair: KeyPair,
    daysValid: Int,
    crlUri: String? = null
): X509Certificate {
    val today = LocalDate.now(ZoneOffset.UTC)
    val until = today.plusDays(daysValid.toLong())
    return generateSelfSignedCertWithDates(subjectDn, keyPair, today, until, crlUri)
}

fun generateSelfSignedCertWithDates(
    subjectDn: String,
    keyPair: KeyPair,
    notBefore: LocalDate,
    notAfter: LocalDate,
    crlUri: String? = null
): X509Certificate {
    val notBeforeDate = Date.from(notBefore.atStartOfDay(ZoneOffset.UTC).toInstant())
    val notAfterDate = Date.from(notAfter.atStartOfDay(ZoneOffset.UTC).toInstant())
    val builder = JcaX509v3CertificateBuilder(
        X500Name(subjectDn),
        BigInteger.valueOf(System.currentTimeMillis()),
        notBeforeDate,
        notAfterDate,
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

    val signer = JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.private)
    return JcaX509CertificateConverter().getCertificate(withCrl.build(signer))
}

fun writePem(file: File, obj: Any) {
    file.writer().use { writer ->
        JcaPEMWriter(writer).use { pemWriter ->
            pemWriter.writeObject(obj)
        }
    }
}
