package no.elhub.auth.features.requests.create.model

import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.CreateRequestBusinessMeta

data class CreateRequestModel(
    val authorizedParty: AuthorizationParty,
    val requestType: AuthorizationRequest.Type,
    val coreMeta: CreateRequestCoreMeta,
    val businessMeta: CreateRequestBusinessMeta,
)

data class CreateRequestCoreMeta(
    val requestedBy: PartyIdentifier,
    val requestedFrom: PartyIdentifier,
    val requestedTo: PartyIdentifier,
)
