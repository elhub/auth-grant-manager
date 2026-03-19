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
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
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
)

// Tries to behave similarly to iText's TsaClientBouncyCastle but without
// any communication with online servers.
class InMemoryTsaClient(
    private val config: TsaConfig
) : ITSAClient {

    // These could be part of the TSA config, if we have the need for other algorithms than SHA-256
    private val digestAlgoName = "SHA-256"
    private val algoId = TSPAlgorithms.SHA256
    private val signatureAlgoName = "SHA256withRSA"

    private val providerName = "BC"
    private var tokenSizeEstimate = 4096
    override fun getTokenSizeEstimate(): Int = tokenSizeEstimate
    override fun getMessageDigest(): MessageDigest = MessageDigest.getInstance(digestAlgoName, providerName)
    override fun getTimeStampToken(imprint: ByteArray): ByteArray {
        val request = TimeStampRequestGenerator().apply {
            setCertReq(true)
        }.generate(algoId, imprint, BigInteger(64, SecureRandom()))

        val digestProvider = JcaDigestCalculatorProviderBuilder()
            .setProvider(providerName)
            .build()
        val contentSigner = JcaContentSignerBuilder(signatureAlgoName)
            .setProvider(providerName)
            .build(config.privateKey)
        val signerInfo = JcaSignerInfoGeneratorBuilder(digestProvider)
            .setSignedAttributeGenerator(DefaultSignedAttributeTableGenerator())
            .build(contentSigner, config.certificate)
        val tokenGenerator = TimeStampTokenGenerator(
            signerInfo,
            digestProvider.get(AlgorithmIdentifier(algoId)),
            ASN1ObjectIdentifier("1.2.3.4.5")
        ).apply {
            addCertificates(JcaCertStore(config.chain))
        }

        val response = TimeStampResponseGenerator(tokenGenerator, setOf(algoId))
            .generateGrantedResponse(request, BigInteger(64, SecureRandom()), Date(), "Operation Okay")

        val encoded = requireNotNull(response.timeStampToken) {
            "In-memory TSA failed to return a timestamp token"
        }.encoded
        tokenSizeEstimate = encoded.size + 32
        return encoded
    }
}
