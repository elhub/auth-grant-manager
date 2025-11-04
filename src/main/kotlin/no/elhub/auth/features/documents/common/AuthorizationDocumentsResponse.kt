package no.elhub.auth.features.documents.common

import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.common.AuthorizationParty
import java.util.UUID

typealias AuthorizationDocumentsResponse = List<AuthorizationDocumentResponse>

fun List<AuthorizationDocument>.toResponse(
    partyMap: Map<UUID, AuthorizationParty>
) = this.map { doc ->
    val requestedByParty = partyMap[doc.requestedBy]
        ?: throw IllegalStateException("requestedBy not found: ${doc.requestedBy}")
    val requestedFromParty = partyMap[doc.requestedFrom]
        ?: throw IllegalStateException("requestedFrom not found: ${doc.requestedFrom}")
    doc.toResponse(requestedByParty, requestedFromParty)
}
