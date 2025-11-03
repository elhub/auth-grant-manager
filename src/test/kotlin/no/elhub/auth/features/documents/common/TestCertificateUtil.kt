package no.elhub.auth.features.documents.common

import java.io.File

object TestCertificateUtil {

    object Constants {
        private const val LOCATION = "build/tmp/test-certs"
        const val CERTIFICATE_LOCATION = "$LOCATION/self-signed-cert.pem"
        const val PRIVATE_KEY_LOCATION = "$LOCATION/self-signed-key.pem"
    }

    init {
        val certFile = File(Constants.CERTIFICATE_LOCATION)
        val keyFile = File(Constants.PRIVATE_KEY_LOCATION)

        require(certFile.exists()) {
            "Certificate file is missing: ${certFile.absolutePath}, run generateTestCerts task to create it."
        }

        require(keyFile.exists()) {
            "Private key file is missing: ${keyFile.absolutePath}, run generateTestCerts task to create it."
        }
    }
}
