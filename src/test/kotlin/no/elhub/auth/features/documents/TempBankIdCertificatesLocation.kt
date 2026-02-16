package no.elhub.auth.features.documents

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.FileWriter
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

class TempBankIdCertificatesLocation(
    val bankIdRootCertificatesDir: String,
    val primaryRoot: BankIdRootFiles,
    val secondaryRoot: BankIdRootFiles
) {
    data class BankIdRootFiles(
        val certificatePath: String,
        val privateKeyPath: String
    )

    val bankIdRootCertificatePath: String
        get() = primaryRoot.certificatePath

    val bankIdRootPrivateKeyPath: String
        get() = primaryRoot.privateKeyPath

    companion object {
        fun create(): TempBankIdCertificatesLocation {
            Security.addProvider(BouncyCastleProvider())

            val baseDir = Files.createTempDirectory("auth-grant-manager-test-certs")
            val bankIdCertsDir = Files.createDirectories(baseDir.resolve("bankid/certs"))
            val bankIdKeysDir = Files.createDirectories(baseDir.resolve("bankid/keys"))

            val primaryRoot = createRoot(
                name = "bankid-root-1",
                subjectDn = "CN=Test BankID Root 1",
                bankIdCertsDir = bankIdCertsDir,
                bankIdKeysDir = bankIdKeysDir
            )

            val secondaryRoot = createRoot(
                name = "bankid-root-2",
                subjectDn = "CN=Test BankID Root 2",
                bankIdCertsDir = bankIdCertsDir,
                bankIdKeysDir = bankIdKeysDir
            )

            return TempBankIdCertificatesLocation(
                bankIdRootCertificatesDir = bankIdCertsDir.toString(),
                primaryRoot = primaryRoot,
                secondaryRoot = secondaryRoot
            )
        }
    }

    fun getRoot(index: Int): BankIdRootFiles =
        when (index) {
            0 -> primaryRoot
            1 -> secondaryRoot
            else -> error("Only roots 0 and 1 are pre-created.")
        }
}

private fun generateKeyPair(): KeyPair =
    KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()

private fun generateSelfSignedRootCert(
    subjectDn: String,
    keyPair: KeyPair,
    daysValid: Long
): X509Certificate {
    val now = Instant.now()
    val notBefore = Date.from(now.minus(1, ChronoUnit.MINUTES))
    val notAfter = Date.from(now.plus(daysValid, ChronoUnit.DAYS))
    val subject = X500Name(subjectDn)
    val serial = BigInteger.valueOf(now.toEpochMilli())

    val builder = JcaX509v3CertificateBuilder(
        subject,
        serial,
        notBefore,
        notAfter,
        subject,
        keyPair.public
    ).apply {
        addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign)
        )
    }

    val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(keyPair.private)
    return JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
}

private fun writePem(path: Path, obj: Any) {
    FileWriter(path.toFile()).use { writer ->
        JcaPEMWriter(writer).use { pemWriter ->
            pemWriter.writeObject(obj)
        }
    }
}

private fun createRoot(
    name: String,
    subjectDn: String,
    bankIdCertsDir: Path,
    bankIdKeysDir: Path
): TempBankIdCertificatesLocation.BankIdRootFiles {
    val keyPair = generateKeyPair()
    val cert = generateSelfSignedRootCert(
        subjectDn = subjectDn,
        keyPair = keyPair,
        daysValid = 3650
    )

    val keyPath = bankIdKeysDir.resolve("$name-key.pem")
    val certPath = bankIdCertsDir.resolve("$name-cert.pem")

    writePem(keyPath, keyPair.private)
    writePem(certPath, cert)

    return TempBankIdCertificatesLocation.BankIdRootFiles(
        certificatePath = certPath.toString(),
        privateKeyPath = keyPath.toString()
    )
}
