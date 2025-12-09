package no.elhub.auth.features.grants.common

import kotlinx.datetime.LocalDate

data class CreateGrantProperties(
    val validTo: LocalDate,
    val validFrom: LocalDate,
)

