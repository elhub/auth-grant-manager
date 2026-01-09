package no.elhub.auth.features.documents.create.model

import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.dto.CreateDocumentMeta

data class CreateDocumentModel(
    val authorizedParty: AuthorizationParty,
    val documentType: AuthorizationDocument.Type,
    val meta: CreateDocumentMeta,
)
