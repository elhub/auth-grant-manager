package no.elhub.auth.features.requests.create.command

import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.requests.AuthorizationRequest
import java.time.OffsetDateTime

interface RequestMetaMarker {
    fun toMetaAttributes(): Map<String, String>
}

data class RequestCommand(
    val type: AuthorizationRequest.Type,
    val requestedFrom: PartyIdentifier,
    val requestedBy: PartyIdentifier,
    val requestedTo: PartyIdentifier,
    val validTo: OffsetDateTime,
    val meta: RequestMetaMarker,
)
