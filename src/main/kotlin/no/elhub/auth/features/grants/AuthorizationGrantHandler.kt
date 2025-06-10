package no.elhub.auth.features.grants

import arrow.core.Either
import arrow.core.left
import no.elhub.auth.model.AuthorizationGrant
import java.util.UUID

class AuthorizationGrantHandler {
    fun getAllGrantsNew(): Either<AuthorizationGrantError, List<AuthorizationGrant>> = AuthorizationGrantRepository.findAll()

    fun getGrantByIdNew(idParam: String?): Either<AuthorizationGrantError, AuthorizationGrant> {
        // handle if the id is invalid or malformed. Should be UUID formatted
        val id =
            try {
                UUID.fromString(idParam)
            } catch (e: IllegalArgumentException) {
                return AuthorizationGrantError.IllegalArgumentError.left()
            }

        return AuthorizationGrantRepository.findById(id)
    }
}
