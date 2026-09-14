package no.elhub.auth.features.grants.common

import arrow.core.Either
import no.elhub.auth.config.TransactionContext
import no.elhub.auth.features.common.RepositoryWriteError
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert

interface AuditLogRepository {
    suspend fun insert(entry: AuthorizationAuditLog): Either<RepositoryWriteError, Unit>
}

class ExposedAuditLogRepository(
    private val transactionContext: TransactionContext,
) : AuditLogRepository {

    override suspend fun insert(entry: AuthorizationAuditLog): Either<RepositoryWriteError, Unit> =
        transactionContext(
            "db_operations",
            "AuditLogRepository",
            "insert",
            { RepositoryWriteError.UnexpectedError },
        ) {
            AuthorizationAuditLogTable.insert {
                it[authorizationGrantId] = entry.grantId
                it[changedAt] = entry.changedAt
                it[changedBy] = entry.changedBy
                it[valueChanged] = entry.valueChanged
                it[valueBefore] = entry.valueBefore
                it[valueAfter] = entry.valueAfter
                it[message] = entry.message
            }
        }
}

object AuthorizationAuditLogTable : UUIDTable("auth.authorization_audit_log") {
    val authorizationGrantId = javaUUID("authorization_grant_id")
        .references(AuthorizationGrantTable.id, onDelete = ReferenceOption.CASCADE)
    val changedAt = timestampWithTimeZone("changed_at")
    val changedBy = varchar("changed_by", 64)
    val valueChanged = varchar("value_changed", 64).nullable()
    val valueBefore = varchar("value_before", 255).nullable()
    val valueAfter = varchar("value_after", 255).nullable()
    val message = text("message").nullable()
}
