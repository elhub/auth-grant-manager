package no.elhub.auth.features.documents

import java.io.File

object TestCertificateUtil {
    object Constants {
        private const val LOCATION = "build/tmp/test-certs"
        const val CERTIFICATE_LOCATION = "$LOCATION/self-signed-cert.pem"
        const val PRIVATE_KEY_LOCATION = "$LOCATION/self-signed-key.pem"
        const val BANKID_ROOT_CERTIFICATE_LOCATION = "$LOCATION/bankid-root-cert.pem"
        const val BANKID_ROOT_PRIVATE_KEY_LOCATION = "$LOCATION/bankid-root-key.pem"
    }

    init {
        val certFile = File(Constants.CERTIFICATE_LOCATION)
        val keyFile = File(Constants.PRIVATE_KEY_LOCATION)
        val bankIdCertFile = File(Constants.BANKID_ROOT_CERTIFICATE_LOCATION)
        val bankIdKeyFile = File(Constants.BANKID_ROOT_PRIVATE_KEY_LOCATION)

        require(certFile.exists()) {
            "Certificate file is missing: ${certFile.absolutePath}, run generateTestCerts task to create it."
        }

        require(keyFile.exists()) {
            "Private key file is missing: ${keyFile.absolutePath}, run generateTestCerts task to create it."
        }

        require(bankIdCertFile.exists()) {
            "BankID root certificate file is missing: ${bankIdCertFile.absolutePath}, run generateTestCerts task to create it."
        }

        require(bankIdKeyFile.exists()) {
            "BankID root private key file is missing: ${bankIdKeyFile.absolutePath}, run generateTestCerts task to create it."
        }
    }
}
