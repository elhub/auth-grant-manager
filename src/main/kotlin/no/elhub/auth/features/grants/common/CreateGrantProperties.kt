package no.elhub.auth.features.grants.common

import kotlinx.datetime.LocalDate
import no.elhub.auth.features.common.AuthorizationMetadata

data class CreateGrantProperties(
    val validTo: LocalDate,
    val validFrom: LocalDate,
    val meta: AuthorizationMetadata = emptyMap()
)
