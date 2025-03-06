package no.elhub.devxp

import no.elhub.devxp.model.AuthorizationGrant
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

fun databaseHasGrant(grantId: String): Boolean {
    var hasGrant = false
    transaction {
        hasGrant = AuthorizationGrant.select { AuthorizationGrant.id eq grantId }.count() > 0
    }
    return hasGrant
}
