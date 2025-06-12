package no.elhub.auth.features.grants

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.elhub.auth.model.AuthorizationGrant
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.SQLException
import java.util.UUID

object AuthorizationGrantRepository {
    fun findAll(): Either<AuthorizationGrantProblem, List<AuthorizationGrant>> =
        try {
            transaction {
                AuthorizationGrant.Entity
                    .selectAll()
                    .associate { it[AuthorizationGrant.Entity.id].toString() to AuthorizationGrant(it) }
                    .values
                    .toList()
            }.right()
        } catch (sqlEx: SQLException) {
            AuthorizationGrantProblem.DataBaseError.left()
        } catch (exp: Exception) {
            AuthorizationGrantProblem.UnexpectedError.left()
        }

    fun findById(id: UUID): Either<AuthorizationGrantProblem, AuthorizationGrant> =
        try {
            transaction {
                AuthorizationGrant.Entity
                    .selectAll()
                    .where { AuthorizationGrant.Entity.id eq id }
                    .singleOrNull()
                    ?.let { AuthorizationGrant(it) }
            }?.right()
                ?: AuthorizationGrantProblem.NotFoundError.left()
        } catch (sqlEx: SQLException) {
            AuthorizationGrantProblem.DataBaseError.left()
        } catch (exp: Exception) {
            AuthorizationGrantProblem.UnexpectedError.left()
        }
}
