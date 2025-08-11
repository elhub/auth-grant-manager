package no.elhub.auth.grantmanager.domain.services

interface ExternalSigningService {
    /**
     * @property digest Data to sign in bytes
     * @return Signature for the provided input digest
     */
    suspend fun sign(digest: ByteArray): ByteArray
}
