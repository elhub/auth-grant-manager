package no.elhub.auth.features.documents.create

import no.elhub.auth.features.documents.AuthorizationDocument

data class CreateDocumentCommand(
    val type: AuthorizationDocument.Type,
    val requestedBy: String,
    val requestedTo: String,
    val meteringPoint: String
)
