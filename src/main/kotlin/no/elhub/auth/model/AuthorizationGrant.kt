package no.elhub.auth.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

data class AuthorizationGrant(
    val id: String,
    val status: String,
    val grantSourceType: String,
    val grantSourceId: String,
    val grantedFor: String,
    val grantedBy: String,
    val grantedAt: LocalDateTime,
    val validFrom: LocalDateTime,
    val validTo: LocalDateTime,
) {
    /*
    object Entity : Table("consent.authorization_grant") {
        val id = varchar("id", 36)
        val grantedFor = varchar("granted_for", 36)
        val grantedBy = varchar("granted_by", 36)
        val grantedAt = datetime("granted_at")

        override val primaryKey = PrimaryKey(id)
    }*/

    @Serializable
    data class Response(
        val meta: ResponseMeta
    )
}
