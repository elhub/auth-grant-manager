package no.elhub.auth.data.persist.tables

import no.elhub.auth.domain.shared.ElhubResource
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object AuthorizationPartyTable : LongIdTable("authorization_party") {
    val type =
        customEnumeration(
            name = "type",
            fromDb = { value -> ElhubResource.valueOf(value as String) },
            toDb = { PGEnum("elhub_resource", it) }
        )
    val descriptor = varchar("descriptor", length = 256)
    val createdAt = timestamp("created_at").clientDefault { java.time.Instant.now() }
}
