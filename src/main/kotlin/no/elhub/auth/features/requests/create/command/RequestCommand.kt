package no.elhub.auth.features.requests.create.command

import no.elhub.auth.features.common.PartyIdentifier
import no.elhub.auth.features.requests.AuthorizationRequest

sealed interface RequestMetaMarker {
    fun toMetaAttributes(): Map<String, String>
}

sealed class RequestCommand(
    val requestedFrom: PartyIdentifier,
    val requestedBy: PartyIdentifier,
    val requestedTo: PartyIdentifier,
    val validTo: String,
    val meta: RequestMetaMarker,
)

fun RequestCommand.toAuthorizationRequestType(): AuthorizationRequest.Type = when (this) {
    is ChangeOfSupplierRequestCommand -> AuthorizationRequest.Type.ChangeOfSupplierConfirmation
}
