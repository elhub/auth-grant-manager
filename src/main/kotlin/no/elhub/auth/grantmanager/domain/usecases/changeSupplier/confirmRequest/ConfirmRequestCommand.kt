package no.elhub.auth.grantmanager.domain.usecases.changeSupplier.confirmRequest

data class ConfirmRequestCommand(val requestId: String, val contract: ByteArray?)
