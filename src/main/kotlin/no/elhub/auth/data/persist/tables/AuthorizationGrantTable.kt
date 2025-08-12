package no.elhub.auth.data.persist.tables

import kotlinx.datetime.toKotlinLocalDateTime
import no.elhub.auth.domain.grant.AuthorizationGrant
import no.elhub.auth.domain.grant.GrantStatus
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object AuthorizationGrantTable : UUIDTable("authorization_grant") {
    val grantStatus =
        customEnumeration(
            name = "status",
            fromDb = { value -> GrantStatus.valueOf(value as String) },
            toDb = { PGEnum("authorization_grant_status", it) },
        )
    val grantedFor = long("granted_for").references(AuthorizationPartyTable.id)
    val grantedBy = long("granted_by").references(AuthorizationPartyTable.id)
    val grantedTo = long("granted_to").references(AuthorizationPartyTable.id)
    val grantedAt = datetime("granted_at").defaultExpression(CurrentDateTime)
    val validFrom = datetime("valid_from").defaultExpression(CurrentDateTime)
    val validTo = datetime("valid_to").defaultExpression(CurrentDateTime)
}

fun ResultRow.toAuthorizationGrant() = AuthorizationGrant(
    id = this[AuthorizationGrantTable.id].toString(),
    grantStatus = this[AuthorizationGrantTable.grantStatus],
    grantedFor = this[AuthorizationGrantTable.grantedFor],
    grantedBy = this[AuthorizationGrantTable.grantedBy],
    grantedTo = this[AuthorizationGrantTable.grantedTo],
    grantedAt = this[AuthorizationGrantTable.grantedAt].toKotlinLocalDateTime(),
    validFrom = this[AuthorizationGrantTable.validFrom].toKotlinLocalDateTime(),
    validTo = this[AuthorizationGrantTable.validTo].toKotlinLocalDateTime(),
)
