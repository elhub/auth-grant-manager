package no.elhub.auth.v1.domain

import no.elhub.auth.common.auth.AuthorizationParty

data class GrantPartyFilters(
    val grantedBy: List<AuthorizationParty> = emptyList(),
    val principal: List<AuthorizationParty> = emptyList(),
    val approvedBy: List<AuthorizationParty> = emptyList(),
    val grantedTo: List<AuthorizationParty> = emptyList(),
)
