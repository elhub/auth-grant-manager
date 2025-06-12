package no.elhub.auth.features.grants

import arrow.core.Either
import no.elhub.auth.model.AuthorizationGrant
import java.util.UUID

class AuthorizationGrantHandler {
    fun getAllGrants(): Either<AuthorizationGrantProblem, List<AuthorizationGrant>> = AuthorizationGrantRepository.findAll()

    fun getGrantById(id: UUID): Either<AuthorizationGrantProblem, AuthorizationGrant> = AuthorizationGrantRepository.findById(id)
}
