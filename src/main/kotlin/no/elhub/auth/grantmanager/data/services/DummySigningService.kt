package no.elhub.auth.grantmanager.data.services

import no.elhub.auth.grantmanager.domain.services.ExternalSigningService
import java.util.Base64

class DummySigningService : ExternalSigningService {
    override suspend fun sign(digest: ByteArray): ByteArray {
        val b64 = Base64.getEncoder().encodeToString(digest)

        // TODO: Dummy signing
        val raw = b64

        return Base64.getDecoder().decode(raw)
    }
}
