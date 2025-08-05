package no.elhub.auth.features.requests

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.elhub.auth.config.ID
import no.elhub.auth.features.utils.validateAuthorizationRequest
import no.elhub.auth.features.utils.validateId
import java.util.UUID

fun Route.requests(requestHandler: AuthorizationRequestHandler) {
    get {
        val requests = requestHandler.getAllRequests()
        call.respond(
            HttpStatusCode.OK,
            message = requests.toGetAuthorizationRequestsResponse()
        )
    }

    get("/{$ID}") {
        val requestId: UUID = validateId(call.parameters[ID])
        val request = requestHandler.getRequestById(requestId)
        call.respond(
            status = HttpStatusCode.OK,
            message = request.toGetAuthorizationRequestResponse()
        )
    }

    post {
        val payload = validateAuthorizationRequest(call.receive())
        val response = requestHandler.postRequest(payload)
        call.respond(
            status = HttpStatusCode.Created,
            message = response.toGetAuthorizationRequestResponse()
        )

    }
}
