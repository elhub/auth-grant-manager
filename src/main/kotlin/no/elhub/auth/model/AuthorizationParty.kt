package no.elhub.auth.model

import kotlinx.datetime.Instant
import no.elhub.auth.utils.PGEnum
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

data class AuthorizationParty(
    val id: Long,
    val type: AuthorizationPartyType,
    val descriptor: String,
    val createdAt: Instant
) {
    object Entity : LongIdTable("authorization_party") {
        val type =
            customEnumeration(
                name = "type",
                fromDb = { value -> AuthorizationPartyType.valueOf(value as String) },
                toDb = { PGEnum("authorization_party_type", it) }
            )
        val descriptor = varchar("descriptor", length = 256)
        val createdAt = timestamp("created_at").clientDefault { java.time.Instant.now() }
    }
}
