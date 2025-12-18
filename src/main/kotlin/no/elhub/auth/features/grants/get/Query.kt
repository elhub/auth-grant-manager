package no.elhub.auth.features.grants.get

import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyIdentifier
import java.util.UUID

sealed class Query {
    abstract val id: UUID

    data class GrantedTo(
        override val id: UUID,
        val grantedTo: AuthorizationParty
    ) : Query()

    data class GrantedFor(
        override val id: UUID,
        val grantedFor: AuthorizationParty
    ) : Query()
}
