package no.elhub.auth.features.requests.create.command

import no.elhub.auth.features.common.CreateScopeData
import no.elhub.auth.features.common.party.PartyIdentifier
import no.elhub.auth.features.requests.AuthorizationRequest
import java.time.OffsetDateTime

const val TEXT_VERSION_KEY = "textVersion"

interface RequestMetaMarker {
    fun toRequestMetaAttributes(): Map<String, String>
}

fun Map<String, String>.withTextVersion(version: String): Map<String, String> =
    this + (TEXT_VERSION_KEY to version)

data class RequestCommand(
    val type: AuthorizationRequest.Type,
    val requestedFrom: PartyIdentifier,
    val requestedBy: PartyIdentifier,
    val requestedTo: PartyIdentifier,
    val validTo: OffsetDateTime,
    val scopes: List<CreateScopeData>,
    val meta: RequestMetaMarker,
)
