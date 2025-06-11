package no.elhub.auth.features.grants

import arrow.core.Either
import no.elhub.auth.model.AuthorizationGrant
import java.util.UUID

class AuthorizationGrantHandler {
    fun getAllGrants(): Either<AuthorizationGrantError, List<AuthorizationGrant>> = AuthorizationGrantRepository.findAll()

    fun getGrantById(id: UUID): Either<AuthorizationGrantError, AuthorizationGrant> = AuthorizationGrantRepository.findById(id)
}
