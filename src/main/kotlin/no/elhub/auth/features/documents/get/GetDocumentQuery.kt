package no.elhub.auth.features.documents.get

import java.util.UUID
import no.elhub.auth.features.documents.AuthorizationDocument

data class GetDocumentQuery(
    val id: UUID
)
