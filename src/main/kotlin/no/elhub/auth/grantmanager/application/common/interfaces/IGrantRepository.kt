package no.elhub.auth.grantmanager.application.common.interfaces

import arrow.core.Either
import no.elhub.auth.grantmanager.domain.models.AuthorizationGrant
import java.util.UUID

interface IGrantRepository {
    fun create(grant: AuthorizationGrant)
    fun get(id: UUID): Either<GrantRepoRetrievalError, AuthorizationGrant>
    fun update(grant: AuthorizationGrant)
    fun delete(id: UUID)
}

sealed class GrantRepoRetrievalError {
    data object NotFound : GrantRepoRetrievalError()
    data class SystemError(val reason: String) : GrantRepoRetrievalError()
}
