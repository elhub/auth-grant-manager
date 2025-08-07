package no.elhub.auth.domain.grant

import arrow.core.Either
import no.elhub.auth.data.persist.repositories.AuthorizationGrantRepository
import no.elhub.auth.domain.shared.AuthorizationScope
import java.util.UUID

class AuthorizationGrantHandler {
    fun getAllGrants(): Either<AuthorizationGrantProblem, AuthorizationGrantRepository.GrantsWithParties> = AuthorizationGrantRepository.findAll()

    fun getGrantById(id: UUID): Either<AuthorizationGrantProblem, AuthorizationGrantRepository.GrantWithParties> = AuthorizationGrantRepository.findById(id)

    fun getGrantScopesById(id: UUID): Either<AuthorizationGrantProblem, List<AuthorizationScope>> = AuthorizationGrantRepository.findScopesById(id)
}
