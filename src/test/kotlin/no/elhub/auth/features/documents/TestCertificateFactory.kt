package no.elhub.auth.features.documents

import no.elhub.auth.features.documents.create.CERT_TYPE
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERPrintableString
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileReader
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

class TestCertificateFactory(
    private val defaultValidity: Duration = Duration.ofMinutes(30),
    private val bankIdRootCertificatePath: String? = null,
    private val bankIdRootPrivateKeyPath: String? = null
) {
    val trustedBankIdRootCertificate: X509Certificate = readRootCertificate(bankIdRootCertificatePath)
    val trustedBankIdRootKeyPair: KeyPair = readRootKeyPair(trustedBankIdRootCertificate, bankIdRootPrivateKeyPath)

    data class FakeElhubCerts(
        val signingKey: KeyPair,
        val signingCert: X509Certificate,
        val chain: List<X509Certificate>
    )

    fun generateKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    fun generateLeafCertificateWithNationalId(
        keyPair: KeyPair,
        issuerKeyPair: KeyPair,
        issuerCertificate: X509Certificate,
        nationalIdentityNumber: String,
        notBeforeOverride: Instant?,
        notAfterOverride: Instant?
    ): X509Certificate {
        ensureBouncyCastle()
        val now = Instant.now()
        val notBeforeInstant = notBeforeOverride ?: now.minus(1, ChronoUnit.MINUTES)
        val notAfterInstant = notAfterOverride ?: notBeforeInstant.plus(defaultValidity)
        val notBefore = Date.from(notBeforeInstant)
        val notAfter = Date.from(notAfterInstant)
        val serial = BigInteger.valueOf(now.toEpochMilli())
        val subject = X500Name("CN=Test Signer")
        val issuer = X500Name(issuerCertificate.subjectX500Principal.name)

        val builder = JcaX509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public
        ).apply {
            addExtension(Extension.basicConstraints, true, BasicConstraints(false))
            addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
            addExtension(
                ASN1ObjectIdentifier(NATIONAL_ID_OID),
                false,
                DERPrintableString(nationalIdentityNumber)
            )
        }

        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(issuerKeyPair.private)
        return JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
    }

    fun generateLeafCertificateWithoutNationalId(
        keyPair: KeyPair,
        issuerKeyPair: KeyPair,
        issuerCertificate: X509Certificate
    ): X509Certificate {
        ensureBouncyCastle()
        val now = Instant.now()
        val notBeforeInstant = now.minus(1, ChronoUnit.MINUTES)
        val notAfterInstant = notBeforeInstant.plus(defaultValidity)
        val notBefore = Date.from(notBeforeInstant)
        val notAfter = Date.from(notAfterInstant)
        val serial = BigInteger.valueOf(now.toEpochMilli())
        val subject = X500Name("CN=Test Signer")
        val issuer = X500Name(issuerCertificate.subjectX500Principal.name)

        val builder = JcaX509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public
        ).apply {
            addExtension(Extension.basicConstraints, true, BasicConstraints(false))
            addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        }

        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(issuerKeyPair.private)
        return JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
    }

    fun generateTsaCertificate(
        keyPair: KeyPair,
        issuerKeyPair: KeyPair,
        issuerCertificate: X509Certificate
    ): X509Certificate {
        ensureBouncyCastle()
        val now = Instant.now()
        val notBefore = Date.from(now.minus(1, ChronoUnit.MINUTES))
        val notAfter = Date.from(now.plus(defaultValidity))
        val serial = BigInteger.valueOf(now.toEpochMilli())
        val subject = X500Name("CN=Test TSA")
        val issuer = X500Name(issuerCertificate.subjectX500Principal.name)

        val builder = JcaX509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public
        ).apply {
            addExtension(Extension.basicConstraints, true, BasicConstraints(false))
            addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
            addExtension(Extension.extendedKeyUsage, true, ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping))
        }

        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(issuerKeyPair.private)
        return JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
    }

    fun generateSelfSignedRootCertificate(keyPair: KeyPair, templateCertificate: X509Certificate): X509Certificate {
        val subject = X500Name(templateCertificate.subjectX500Principal.name)
        val serial = templateCertificate.serialNumber
        return generateSelfSignedRootCertificate(keyPair, subject, serial)
    }

    // Generates a fake Elhub signing certificate to test forged Elhub signature scenarios.
    fun generateFakeElhubCertificates(expectedElhubCert: X509Certificate): FakeElhubCerts {
        val issuerKeyPair = generateKeyPair()
        val issuerSubject = X500Name(expectedElhubCert.issuerX500Principal.name)
        val rootCert = generateSelfSignedRootCertificate(issuerKeyPair, issuerSubject, BigInteger.valueOf(Instant.now().toEpochMilli()))
        val signingKeyPair = generateKeyPair()
        val leafCert = generateLeafCertificateWithNationalId(
            keyPair = signingKeyPair,
            issuerKeyPair = issuerKeyPair,
            issuerSubject = issuerSubject,
            serial = expectedElhubCert.serialNumber
        )
        return FakeElhubCerts(
            signingKey = signingKeyPair,
            signingCert = leafCert,
            chain = listOf(leafCert, rootCert)
        )
    }

    private fun generateSelfSignedRootCertificate(
        keyPair: KeyPair,
        subject: X500Name,
        serial: BigInteger
    ): X509Certificate {
        ensureBouncyCastle()
        val now = Instant.now()
        val notBefore = Date.from(now.minus(1, ChronoUnit.MINUTES))
        val notAfter = Date.from(now.plus(defaultValidity))

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

    private fun generateLeafCertificateWithNationalId(
        keyPair: KeyPair,
        issuerKeyPair: KeyPair,
        issuerSubject: X500Name,
        serial: BigInteger
    ): X509Certificate {
        ensureBouncyCastle()
        val now = Instant.now()
        val notBefore = Date.from(now.minus(1, ChronoUnit.MINUTES))
        val notAfter = Date.from(now.plus(defaultValidity))
        val subject = X500Name("CN=Fake Elhub Signer")

        val builder = JcaX509v3CertificateBuilder(
            issuerSubject,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public
        ).apply {
            addExtension(Extension.basicConstraints, true, BasicConstraints(false))
            addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        }

        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(issuerKeyPair.private)
        return JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
    }

    private fun readRootCertificate(pathOverride: String?): X509Certificate =
        readSingleCert(pathOverride ?: TestCertificateUtil.Constants.BANKID_ROOT_CERTIFICATE_LOCATION)

    private fun readRootKeyPair(rootCert: X509Certificate, pathOverride: String?): KeyPair {
        val privateKey = readPrivateKey(pathOverride ?: TestCertificateUtil.Constants.BANKID_ROOT_PRIVATE_KEY_LOCATION)
        return KeyPair(rootCert.publicKey, privateKey)
    }

    private fun readSingleCert(path: String): X509Certificate =
        File(path).inputStream().use {
            CertificateFactory.getInstance(CERT_TYPE)
                .generateCertificates(it)
                .filterIsInstance<X509Certificate>()
                .single()
        }

    private fun readPrivateKey(path: String): PrivateKey {
        ensureBouncyCastle()
        FileReader(path).use { reader ->
            PEMParser(reader).use { parser ->
                val obj = parser.readObject()
                val converter = JcaPEMKeyConverter().setProvider("BC")
                return when (obj) {
                    is PEMKeyPair -> converter.getKeyPair(obj).private
                    is PrivateKeyInfo -> converter.getPrivateKey(obj)
                    else -> error("Unsupported PEM object in $path: ${obj?.javaClass?.name}")
                }
            }
        }
    }

    private fun ensureBouncyCastle() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private companion object {
        const val NATIONAL_ID_OID = "2.16.578.1.61.2.4"
    }
}
