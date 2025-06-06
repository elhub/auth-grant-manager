package no.elhub.auth.features.grants

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.errors.ApiError
import no.elhub.auth.features.errors.ApiError.NotFound
import no.elhub.auth.model.AuthorizationGrant
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.annotation.Single
import java.util.UUID

@Single
class AuthorizationGrantHandler {
    fun getGrants(): List<AuthorizationGrant> =
        transaction {
            AuthorizationGrant.Entity
                .selectAll()
                .associate { it[AuthorizationGrant.Entity.id].toString() to AuthorizationGrant(it) }
                .values
                .toList()
        }

    fun getGrant(id: UUID): Either<ApiError, AuthorizationGrant> =
        either {
            val result =
                transaction {
                    AuthorizationGrant.Entity
                        .selectAll()
                        .where { AuthorizationGrant.Entity.id eq id }
                        .singleOrNull()
                }

            if (result == null) {
                raise(NotFound(detail = "Could not find AuthorizationGrant with id $id."))
            }

            AuthorizationGrant(result)
        }
}
