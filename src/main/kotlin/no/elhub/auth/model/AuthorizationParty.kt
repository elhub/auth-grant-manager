package no.elhub.auth.model

import kotlinx.datetime.Instant
import no.elhub.auth.utils.PGEnum
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

data class AuthorizationParty(
    val id: Long,
    val type: ElhubResource,
    val descriptor: String,
    val createdAt: Instant
) {
    object Entity : LongIdTable("authorization_party") {
        val type =
            customEnumeration(
                name = "type",
                fromDb = { value -> ElhubResource.valueOf(value as String) },
                toDb = { PGEnum("elhub_resource", it) }
            )
        val descriptor = varchar("descriptor", length = 256)
        val createdAt = timestamp("created_at").clientDefault { java.time.Instant.now() }
    }
}
