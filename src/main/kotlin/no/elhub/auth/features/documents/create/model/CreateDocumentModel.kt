package no.elhub.auth.features.documents.create.model

import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.DocumentMeta

data class CreateDocumentModel(
    val documentType: AuthorizationDocument.Type,
    val meta: DocumentMeta,
)
