package no.elhub.auth.features.requests.create.command

import no.elhub.auth.features.common.PartyIdentifier
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.common.AuthorizationRequestProperty
import java.util.UUID

interface RequestMetaMarker {
    fun toMetaAttributes(): Map<String, String>
}

data class RequestCommand(
    val type: AuthorizationRequest.Type,
    val requestedFrom: PartyIdentifier,
    val requestedBy: PartyIdentifier,
    val requestedTo: PartyIdentifier,
    val validTo: String,
    val meta: RequestMetaMarker,
)

fun Map<String, String>.toRequestProperties(requestId: UUID) =
    this.map { (key, value) ->
        AuthorizationRequestProperty(
            requestId = requestId,
            key = key,
            value = value,
        )
    }.toList()
