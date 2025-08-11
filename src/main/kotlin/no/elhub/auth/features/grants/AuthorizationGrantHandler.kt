package no.elhub.auth.features.grants

import arrow.core.Either
import no.elhub.auth.features.errors.DomainError
import no.elhub.auth.model.AuthorizationScope
import java.util.UUID

class AuthorizationGrantHandler {
    fun getAllGrants(): Either<DomainError, AuthorizationGrantRepository.GrantsWithParties> = AuthorizationGrantRepository.findAll()

    fun getGrantById(id: UUID): Either<DomainError, AuthorizationGrantRepository.GrantWithParties> = AuthorizationGrantRepository.findById(id)

    fun getGrantScopesById(id: UUID): Either<DomainError, List<AuthorizationScope>> = AuthorizationGrantRepository.findScopesById(id)
}
