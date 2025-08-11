package no.elhub.auth.grantmanager.domain.usecases.changeSupplier.getRequest

import arrow.core.left
import arrow.core.right
import no.elhub.auth.grantmanager.domain.usecases.changeSupplier.getRequest.GetRequestError
import no.elhub.auth.grantmanager.domain.errors.RepoRetrievalError
import no.elhub.auth.grantmanager.domain.repositories.ChangeSupplierRequestRepository

class GetRequestUseCase(val repo: ChangeSupplierRequestRepository) {
    suspend operator fun invoke(query: GetRequestQuery) =
        repo.get(query.id).fold(
            ifLeft = { repoRetrievalError ->
                when (repoRetrievalError) {
                    RepoRetrievalError.NotFound -> GetRequestError.NotFound.left()
                    else -> GetRequestError.SystemErrorRequest("An error occurred when attempting to retrieve the request").left()
                }
            },
            ifRight = { changeSupplierRequest -> changeSupplierRequest.right() }
        )
}
