package no.elhub.auth.services.requests

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import no.elhub.auth.model.AuthorizationRequest
import no.elhub.auth.model.MetaResponse
import org.koin.core.annotation.Single

@Single
class AuthorizationRequestService {

    suspend fun getRequests(call: ApplicationCall) {
        val response = AuthorizationRequest.Response(
            meta = MetaResponse(),
        )
        call.respond(status = HttpStatusCode.OK, message = response)
    }

    suspend fun postRequest(call: ApplicationCall) {
        val response = AuthorizationRequest.Response(
            meta = MetaResponse(),
        )
        call.respond(status = HttpStatusCode.OK, message = response)
    }

    suspend fun getRequestById(call: ApplicationCall) {
        val response = AuthorizationRequest.Response(
            meta = MetaResponse(),
        )
        call.respond(status = HttpStatusCode.OK, message = response)
    }
}
