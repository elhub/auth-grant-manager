package no.elhub.auth.v0.features.grants.common

import kotlinx.datetime.LocalDate

data class CreateGrantProperties(
    val validTo: LocalDate,
    val validFrom: LocalDate,
    val meta: Map<String, String> = emptyMap()
)
