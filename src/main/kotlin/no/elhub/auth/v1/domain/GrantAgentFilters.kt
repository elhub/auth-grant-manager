package no.elhub.auth.v1.domain

import no.elhub.auth.v1.common.auth.AuthorizationAgent

data class GrantAgentFilters(
    val grantedBy: List<AuthorizationAgent> = emptyList(),
    val principal: List<AuthorizationAgent> = emptyList(),
    val approvedBy: List<AuthorizationAgent> = emptyList(),
    val grantedTo: List<AuthorizationAgent> = emptyList(),
)
