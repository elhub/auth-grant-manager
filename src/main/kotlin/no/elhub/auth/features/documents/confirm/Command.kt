package no.elhub.auth.features.documents.confirm

import java.util.UUID

data class Command(
    val documentId: UUID,
    val signedFile: ByteArray
)
