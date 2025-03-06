package no.elhub.devxp.model

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.annotation.Single
import java.util.UUID

object AuthorizationGrant : Table("consent.authorization_grant") {
    val id = varchar("id", 36)
    val grantedFor = varchar("granted_for", 36)
    val grantedBy = varchar("granted_by", 36)
    val grantedAt = datetime("granted_at")

    override val primaryKey = PrimaryKey(id)
}
