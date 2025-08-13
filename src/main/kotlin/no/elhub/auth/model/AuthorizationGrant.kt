package no.elhub.auth.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import no.elhub.auth.utils.PGEnum
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

data class AuthorizationGrant(
    val id: String,
    val grantStatus: GrantStatus,
    val grantedFor: Long,
    val grantedBy: Long,
    val grantedTo: Long,
    val grantedAt: LocalDateTime,
    val validFrom: LocalDateTime,
    val validTo: LocalDateTime,
) {
    object Table : UUIDTable("authorization_grant") {
        val grantStatus =
            customEnumeration(
                name = "status",
                fromDb = { value -> GrantStatus.valueOf(value as String) },
                toDb = { PGEnum("authorization_grant_status", it) },
            )
        val grantedFor = long("granted_for").references(AuthorizationParty.Table.id)
        val grantedBy = long("granted_by").references(AuthorizationParty.Table.id)
        val grantedTo = long("granted_to").references(AuthorizationParty.Table.id)
        val grantedAt = datetime("granted_at").defaultExpression(CurrentDateTime)
        val validFrom = datetime("valid_from").defaultExpression(CurrentDateTime)
        val validTo = datetime("valid_to").defaultExpression(CurrentDateTime)
    }

    constructor(row: ResultRow) : this(
        id = row[Table.id].toString(),
        grantStatus = row[Table.grantStatus],
        grantedFor = row[Table.grantedFor],
        grantedBy = row[Table.grantedBy],
        grantedTo = row[Table.grantedTo],
        grantedAt = row[Table.grantedAt].toKotlinLocalDateTime(),
        validFrom = row[Table.validFrom].toKotlinLocalDateTime(),
        validTo = row[Table.validTo].toKotlinLocalDateTime(),
    )
}
