package no.elhub.auth.grantmanager.application.grants.getGrant

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.grantmanager.application.common.interfaces.GrantRepoRetrievalError
import no.elhub.auth.grantmanager.application.common.interfaces.IGrantRepository
import no.elhub.auth.grantmanager.domain.models.AuthorizationGrant
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class Handler(val repo: IGrantRepository) {
    fun handle(query: Query): Either<GrantRetrievalError, AuthorizationGrant> =
        repo.get(query.id).fold(
            ifLeft = { grantRepoRetrievalError ->
                when (grantRepoRetrievalError) {
                    GrantRepoRetrievalError.NotFound -> GrantRetrievalError.NotFound.left()
                    else -> GrantRetrievalError.SystemError("An error occurred when attempting to retrieve the grant").left()
                }
            },
            ifRight = { grant -> grant.right() }
        )
}

sealed class GrantRetrievalError {
    data object NotFound : GrantRetrievalError()
    data class SystemError(val reason: String) : GrantRetrievalError()
}
