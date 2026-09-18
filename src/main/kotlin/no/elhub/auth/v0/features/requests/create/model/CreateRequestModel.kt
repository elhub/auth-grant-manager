package no.elhub.auth.v0.features.requests.create.model

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v0.features.common.party.PartyIdentifier
import no.elhub.auth.v0.features.requests.AuthorizationRequest
import no.elhub.auth.v0.features.requests.common.CreateRequestBusinessMeta

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
