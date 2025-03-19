package no.elhub.auth.services.grants

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import no.elhub.auth.model.AuthorizationGrant
import no.elhub.auth.model.MetaResponse
import org.koin.core.annotation.Single

@Single
class AuthorizationGrantService {
    suspend fun getGrants(call: ApplicationCall) {
        val response = AuthorizationGrant.Response(
            meta = MetaResponse(),
        )
        call.respond(status = HttpStatusCode.OK, message = response)
    }

    suspend fun getGrantById(call: ApplicationCall) {
        val response = AuthorizationGrant.Response(
            meta = MetaResponse(),
        )
        call.respond(status = HttpStatusCode.OK, message = response)
    }
}
