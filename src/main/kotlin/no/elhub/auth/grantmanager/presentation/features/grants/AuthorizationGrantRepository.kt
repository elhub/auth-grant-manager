package no.elhub.auth.grantmanager.presentation.features.grants

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.datetime.Instant
import no.elhub.auth.grantmanager.data.models.AuthorizationGrantDbEntity
import no.elhub.auth.grantmanager.presentation.model.AuthorizationGrantScopes
import no.elhub.auth.grantmanager.presentation.model.AuthorizationScope
import no.elhub.auth.grantmanager.presentation.model.AuthorizationScopes
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.UUID

object AuthorizationGrantRepository {

    private val logger = LoggerFactory.getLogger(AuthorizationGrantRepository::class.java)

    fun findAll(): Either<AuthorizationGrantProblem, List<AuthorizationGrantDbEntity>> =
        try {
            transaction {
                AuthorizationGrantDbEntity.Entity
                    .selectAll()
                    .associate { it[AuthorizationGrantDbEntity.Entity.id].toString() to AuthorizationGrantDbEntity(it) }
                    .values
                    .toList()
            }.right()
        } catch (sqlEx: SQLException) {
            logger.error("SQL error occurred during fetch all grants': ${sqlEx.message}")
            AuthorizationGrantProblem.DataBaseError.left()
        } catch (exp: Exception) {
            logger.error("Unknown error occurred during fetch all grants: ${exp.message}")
            AuthorizationGrantProblem.UnexpectedError.left()
        }

    fun findById(grantId: UUID): Either<AuthorizationGrantProblem, AuthorizationGrantDbEntity> =
        try {
            transaction {
                AuthorizationGrantDbEntity.Entity
                    .selectAll()
                    .where { AuthorizationGrantDbEntity.Entity.id eq grantId }
                    .singleOrNull()
                    ?.let { AuthorizationGrantDbEntity(it) }
            }?.right()
                ?: AuthorizationGrantProblem.NotFoundError.left()
        } catch (sqlEx: SQLException) {
            logger.error("SQL error occurred during fetch grant by id': ${sqlEx.message}")
            AuthorizationGrantProblem.DataBaseError.left()
        } catch (exp: Exception) {
            logger.error("Unknown error occurred during fetch grant by id: ${exp.message}")
            AuthorizationGrantProblem.UnexpectedError.left()
        }

    fun findScopesById(grantId: UUID): Either<AuthorizationGrantProblem, List<AuthorizationScope>> = try {
        transaction {
            AuthorizationGrantDbEntity.Entity
                .selectAll()
                .where { AuthorizationGrantDbEntity.Entity.id eq grantId }
                .singleOrNull()
                ?.let {
                    (AuthorizationGrantScopes innerJoin AuthorizationScopes)
                        .selectAll()
                        .where { AuthorizationGrantScopes.authorizationGrantId eq grantId }
                        .map { row ->
                            AuthorizationScope(
                                id = row[AuthorizationScopes.id].value,
                                authorizedResourceId = row[AuthorizationScopes.authorizedResourceId],
                                authorizedResourceType = row[AuthorizationScopes.authorizedResourceType],
                                permissionType = row[AuthorizationScopes.permissionType],
                                createdAt = Instant.parse(row[AuthorizationScopes.createdAt].toString())
                            )
                        }
                }
        }?.right() ?: AuthorizationGrantProblem.NotFoundError.left()
    } catch (sqlEx: SQLException) {
        logger.error("SQL error occurred during fetch scope by id': ${sqlEx.message}")
        AuthorizationGrantProblem.DataBaseError.left()
    } catch (exp: Exception) {
        logger.error("Unknown error occurred during fetch scope by id: ${exp.message}")
        AuthorizationGrantProblem.UnexpectedError.left()
    }
}
