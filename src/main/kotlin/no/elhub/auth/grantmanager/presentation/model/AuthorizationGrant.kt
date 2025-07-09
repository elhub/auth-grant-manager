package no.elhub.auth.grantmanager.presentation.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import no.elhub.auth.grantmanager.presentation.utils.PGEnum
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

data class AuthorizationGrant(
    val id: String,
    val grantStatus: GrantStatus,
    val grantedFor: String,
    val grantedBy: String,
    val grantedTo: String,
    val grantedAt: LocalDateTime,
    val validFrom: LocalDateTime,
    val validTo: LocalDateTime,
) {
    object Entity : UUIDTable("authorization_grant") {
        val grantStatus =
            customEnumeration(
                name = "status",
                fromDb = { value -> GrantStatus.valueOf(value as String) },
                toDb = { PGEnum("authorization_grant_status", it) },
            )
        val grantedFor = varchar("granted_for", 16)
        val grantedBy = varchar("granted_by", 16)
        val grantedTo = varchar("granted_to", 16)
        val grantedAt = datetime("granted_at").defaultExpression(CurrentDateTime)
        val validFrom = datetime("valid_from").defaultExpression(CurrentDateTime)
        val validTo = datetime("valid_to").defaultExpression(CurrentDateTime)
    }

    constructor(row: ResultRow) : this(
        id = row[Entity.id].toString(),
        grantStatus = row[Entity.grantStatus],
        grantedFor = row[Entity.grantedFor],
        grantedBy = row[Entity.grantedBy],
        grantedTo = row[Entity.grantedTo],
        grantedAt = row[Entity.grantedAt].toKotlinLocalDateTime(),
        validFrom = row[Entity.validFrom].toKotlinLocalDateTime(),
        validTo = row[Entity.validTo].toKotlinLocalDateTime(),
    )
}
