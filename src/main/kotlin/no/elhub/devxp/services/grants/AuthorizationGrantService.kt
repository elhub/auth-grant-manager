package no.elhub.devxp.services.grants

import no.elhub.devxp.model.AuthorizationGrant
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.annotation.Single
import java.time.LocalDateTime
import java.util.*

@Single
class AuthorizationGrantService {
    fun createGrant(): String {
        val grantId = UUID.randomUUID().toString()
        val grantFor = "some-user-id"
        val grantBy = "some-granter-id"
        val grantAt = LocalDateTime.now()

        transaction {
            AuthorizationGrant.insert {
                it[id] = grantId
                it[grantedFor] = grantFor
                it[grantedBy] = grantBy
                it[grantedAt] = grantAt
            }
        }

        return grantId
    }
}
