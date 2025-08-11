package no.elhub.auth.grantmanager.domain.usecases.changeSupplier.confirmRequest

import java.util.UUID

sealed class ConfirmRequestError {
    class IncorrectContractError : ConfirmRequestError()
    class NoContractAssociatedError() : ConfirmRequestError()
    class ContractNotSignedError : ConfirmRequestError()
    class AlreadyGrantedError() : ConfirmRequestError()
    class PendingContractSubmissionError() : ConfirmRequestError()
    data class RetrievalError(val requestId: UUID) : ConfirmRequestError()
    data class SystemError(val reason: String) : ConfirmRequestError()
}
