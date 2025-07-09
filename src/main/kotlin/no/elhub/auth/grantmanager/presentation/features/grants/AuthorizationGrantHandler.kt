package no.elhub.auth.grantmanager.presentation.features.grants

import arrow.core.Either
import no.elhub.auth.grantmanager.presentation.model.AuthorizationGrant
import no.elhub.auth.grantmanager.presentation.model.AuthorizationScope
import java.util.UUID

class AuthorizationGrantHandler {
    fun getAllGrants(): Either<AuthorizationGrantProblem, List<AuthorizationGrant>> = AuthorizationGrantRepository.findAll()

    fun getGrantById(id: UUID): Either<AuthorizationGrantProblem, AuthorizationGrant> = AuthorizationGrantRepository.findById(id)

    fun getGrantScopesById(id: UUID): Either<AuthorizationGrantProblem, List<AuthorizationScope>> = AuthorizationGrantRepository.findScopesById(id)
}
