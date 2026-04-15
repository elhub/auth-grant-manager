package no.elhub.auth.features.documents.create.command

import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.documents.AuthorizationDocument
import java.time.OffsetDateTime

interface DocumentMetaMarker {
    fun toMetaAttributes(): Map<String, String>
}

data class DocumentCommand(
    val type: AuthorizationDocument.Type,
    val validTo: OffsetDateTime,
    val scopes: List<CreateScopeData>,
    val meta: DocumentMetaMarker,
)
