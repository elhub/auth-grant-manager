package no.elhub.auth.features.documents

import com.itextpdf.signatures.ITSAClient
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.tsp.TSPAlgorithms
import org.bouncycastle.tsp.TimeStampRequestGenerator
import org.bouncycastle.tsp.TimeStampResponseGenerator
import org.bouncycastle.tsp.TimeStampTokenGenerator
import java.math.BigInteger
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date

data class TsaConfig(
    val privateKey: PrivateKey,
    val certificate: X509Certificate,
    val chain: List<X509Certificate>,
    val policyOid: String = "1.2.3.4.5",
    val digestAlgorithm: String = "SHA-256",
    val signatureAlgorithm: String = "SHA256withRSA",
)

class InMemoryTsaClient(
    private val config: TsaConfig
) : ITSAClient {
    private var tokenSizeEstimate = 4096

    override fun getTokenSizeEstimate(): Int = tokenSizeEstimate

    override fun getMessageDigest(): MessageDigest = MessageDigest.getInstance(config.digestAlgorithm, "BC")

    override fun getTimeStampToken(imprint: ByteArray): ByteArray {
        val request = TimeStampRequestGenerator().apply {
            setCertReq(true)
        }.generate(
            digestOidFor(config.digestAlgorithm),
            imprint,
            BigInteger(64, SecureRandom())
        )

        val digestProvider = JcaDigestCalculatorProviderBuilder()
            .setProvider("BC")
            .build()
        val contentSigner = JcaContentSignerBuilder(config.signatureAlgorithm)
            .setProvider("BC")
            .build(config.privateKey)
        val signerInfo = JcaSignerInfoGeneratorBuilder(digestProvider)
            .setSignedAttributeGenerator(DefaultSignedAttributeTableGenerator())
            .build(contentSigner, config.certificate)
        val tokenGenerator = TimeStampTokenGenerator(
            signerInfo,
            digestProvider.get(org.bouncycastle.asn1.x509.AlgorithmIdentifier(digestOidFor(config.digestAlgorithm))),
            ASN1ObjectIdentifier(config.policyOid)
        ).apply {
            addCertificates(JcaCertStore(config.chain))
        }

        val response = TimeStampResponseGenerator(
            tokenGenerator,
            setOf(TSPAlgorithms.SHA256, TSPAlgorithms.SHA384, TSPAlgorithms.SHA512)
        ).generateGrantedResponse(
            request,
            BigInteger(64, SecureRandom()),
            Date(),
            "Operation Okay"
        )

        val encoded = requireNotNull(response.timeStampToken) {
            "In-memory TSA failed to return a timestamp token"
        }.encoded
        tokenSizeEstimate = encoded.size + 32
        return encoded
    }

    private fun digestOidFor(digestAlgorithm: String): ASN1ObjectIdentifier = when (digestAlgorithm.uppercase()) {
        "SHA-256", "SHA256" -> TSPAlgorithms.SHA256
        "SHA-384", "SHA384" -> TSPAlgorithms.SHA384
        "SHA-512", "SHA512" -> TSPAlgorithms.SHA512
        else -> error("Unsupported TSA digest algorithm: $digestAlgorithm")
    }
}
