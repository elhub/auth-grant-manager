package no.elhub.auth.grantmanager.domain.models

import java.util.UUID

open class Document(
    id: UUID,
    val title: String,
    open val bytes: ByteArray,
) : AuditableEntity(id)
