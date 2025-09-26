package no.elhub.auth.features.documents.create

import java.util.UUID

data class EndUser(
    val id: UUID,
    val nin: String
)
