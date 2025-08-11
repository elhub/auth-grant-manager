package no.elhub.auth.grantmanager.domain.models

import java.util.UUID

class SignableDocument(
    id: UUID,
    title: String,
    bytes: ByteArray,
) : Document(id, title, bytes) {

    // TODO: Check bytes is also signed by consumer
    val signed = false
}
