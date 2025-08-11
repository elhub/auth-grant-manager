package no.elhub.auth.grantmanager.domain.usecases.changeSupplier.createRequest

import java.time.Instant

data class CreateRequestCommand(
    val supplierId: String,
    val meteringPointId: String,
    val validUntil: Instant,
    val generateContract: Boolean = false
)
