package no.elhub.auth.features.grants

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import no.elhub.auth.model.AuthorizationGrant
import no.elhub.auth.model.ResponseMeta
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.annotation.Single

@Single
class AuthorizationGrantHandler {
    fun getGrants(): List<AuthorizationGrant> =
        transaction {
            AuthorizationGrant.Entity
                .selectAll()
                .associate { it[AuthorizationGrant.Entity.id].toString() to AuthorizationGrant(it) }
                .values
                .toList()
        }

    suspend fun getGrantById(call: ApplicationCall) {
        val response =
            AuthorizationGrant.Response(
                meta = ResponseMeta(),
            )
        call.respond(status = HttpStatusCode.OK, message = response)
    }
}
