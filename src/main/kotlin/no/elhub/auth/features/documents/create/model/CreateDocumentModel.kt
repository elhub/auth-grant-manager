package no.elhub.auth.features.documents.create.model

import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.CreateDocumentBusinessMeta

data class CreateDocumentModel(
    val authorizedParty: AuthorizationParty,
    val documentType: AuthorizationDocument.Type,
    val coreMeta: CreateDocumentCoreMeta,
    val businessMeta: CreateDocumentBusinessMeta,
)

data class CreateDocumentCoreMeta(
    val requestedBy: PartyIdentifier,
    val requestedFrom: PartyIdentifier,
    val requestedTo: PartyIdentifier,
)
