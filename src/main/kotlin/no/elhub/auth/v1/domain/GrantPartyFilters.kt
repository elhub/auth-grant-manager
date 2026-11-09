package no.elhub.auth.v1.domain

data class GrantPartyFilters(
    val grantedBy: List<String> = emptyList(),
    val principal: List<String> = emptyList(),
    val approvedBy: List<String> = emptyList(),
    val grantedTo: List<String> = emptyList(),
)
