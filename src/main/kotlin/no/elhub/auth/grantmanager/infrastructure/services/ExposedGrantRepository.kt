package no.elhub.auth.grantmanager.infrastructure.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import no.elhub.auth.grantmanager.application.common.interfaces.GrantRepoRetrievalError
import no.elhub.auth.grantmanager.application.common.interfaces.IGrantRepository
import no.elhub.auth.grantmanager.domain.models.AuthorizationGrant
import no.elhub.auth.grantmanager.presentation.features.grants.AuthorizationGrantRepository
import no.elhub.auth.grantmanager.presentation.model.AuthorizationGrantDbEntity
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class ExposedGrantRepository : IGrantRepository {

    private val logger = LoggerFactory.getLogger(AuthorizationGrantRepository::class.java)

    override fun create(grant: AuthorizationGrant) {
        TODO("Not yet implemented")
    }

    override fun get(id: UUID): Either<GrantRepoRetrievalError, AuthorizationGrant> =
        try {
            transaction {
                AuthorizationGrantDbEntity.Entity
                    .selectAll()
                    .where { AuthorizationGrantDbEntity.Entity.id eq id }
                    .singleOrNull()?.let { thing -> AuthorizationGrantDbEntity(thing).toDomainObject() }
            }?.right()
                ?: GrantRepoRetrievalError.NotFound.left()
        } catch (sqlEx: SQLException) {
            logger.error("SQL error occurred during fetch grant by id': ${sqlEx.message}")
            GrantRepoRetrievalError.SystemError("An SQL error occurred when attempting to fetch the grant").left()
        } catch (exp: Exception) {
            logger.error("Unknown error occurred during fetch grant by id: ${exp.message}")
            GrantRepoRetrievalError.SystemError("An unknown error occurred when attempting to retrieve the grant").left()
        }

    override fun update(grant: AuthorizationGrant) {
        TODO("Not yet implemented")
    }

    override fun delete(id: UUID) {
        TODO("Not yet implemented")
    }
}

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
fun AuthorizationGrantDbEntity.toDomainObject() = AuthorizationGrant(
    UUID.fromString(this.id),
    this.grantedFor,
    this.grantedBy,
    this.grantedTo,
    this.grantedAt.toInstant(TimeZone.UTC),
    this.validFrom.toInstant(TimeZone.UTC),
    this.validTo.toInstant(TimeZone.UTC),
)
