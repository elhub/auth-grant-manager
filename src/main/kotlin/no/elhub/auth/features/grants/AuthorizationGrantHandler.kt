package no.elhub.auth.features.grants

import arrow.core.Either
import no.elhub.auth.features.errors.RepositoryError
import no.elhub.auth.model.AuthorizationScope
import java.util.UUID

class AuthorizationGrantHandler {
    fun getAllGrants(): Either<RepositoryError, AuthorizationGrantRepository.GrantsWithParties> = AuthorizationGrantRepository.findAll()

    fun getGrantById(id: UUID): Either<RepositoryError, AuthorizationGrantRepository.GrantWithParties> = AuthorizationGrantRepository.findById(id)

    fun getGrantScopesById(id: UUID): Either<RepositoryError, List<AuthorizationScope>> = AuthorizationGrantRepository.findScopesById(id)
}
