package no.elhub.auth.features.documents

import java.io.File

object TestCertificateUtil {

    object Constants {
        private const val LOCATION = "build/tmp/test-certs"
        const val CERTIFICATE_LOCATION = "$LOCATION/elhub/self-signed-cert.pem"
        const val INTERMEDIATE_CERTIFICATE_LOCATION = "$LOCATION/elhub/intermediate-cert.pem"

        const val PRIVATE_KEY_LOCATION = "$LOCATION/elhub/self-signed-key.pem"
        const val BANKID_ROOT_CERTIFICATE_LOCATION = "$LOCATION/bankid/certs/bankid-root-cert.pem"
        const val BANKID_ROOT_PRIVATE_KEY_LOCATION = "$LOCATION/bankid/keys/bankid-root-key.pem"
        const val BANKID_ROOT_CERTIFICATES_DIR = "$LOCATION/bankid/certs"
        const val TSA_ROOT_CERTIFICATES_DIR = "$LOCATION/bankid/certs"
    }

    init {
        val rootElhubCertFile = File(Constants.CERTIFICATE_LOCATION)
        val intermElhubCertFile = File(Constants.INTERMEDIATE_CERTIFICATE_LOCATION)
        val keyFile = File(Constants.PRIVATE_KEY_LOCATION)
        val bankIdCertFile = File(Constants.BANKID_ROOT_CERTIFICATE_LOCATION)
        val bankIdKeyFile = File(Constants.BANKID_ROOT_PRIVATE_KEY_LOCATION)
        val bankIdCertsDir = File(Constants.BANKID_ROOT_CERTIFICATES_DIR)

        require(rootElhubCertFile.exists()) {
            "Certificate file is missing: ${rootElhubCertFile.absolutePath}, run generateTestCerts task to create it."
        }

        require(intermElhubCertFile.exists()) {
            "Certificate file is missing ${intermElhubCertFile.absolutePath}, run generateTestCerts task to create it."
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

        require(bankIdCertsDir.isDirectory) {
            "BankID root certificates directory is missing: ${bankIdCertsDir.absolutePath}, run generateTestCerts task to create it."
        }
    }
}
