package no.elhub.auth.services.documents

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import no.elhub.auth.model.AuthorizationDocument
import no.elhub.auth.model.ResponseMeta
import org.koin.core.annotation.Single

@Single
class AuthorizationDocumentService {

    suspend fun getDocuments(call: ApplicationCall) {
        val response = AuthorizationDocument.Response(
            meta = ResponseMeta(),
        )
        call.respond(status = HttpStatusCode.OK, message = response)
    }

    suspend fun postDocument(call: ApplicationCall) {
        val response = AuthorizationDocument.Response(
            meta = ResponseMeta(),
        )
        call.respond(status = HttpStatusCode.OK, message = response)
    }

    suspend fun getDocumentById(call: ApplicationCall) {
        val response = AuthorizationDocument.Response(
            meta = ResponseMeta(),
        )
        call.respond(status = HttpStatusCode.OK, message = response)
    }

    suspend fun patchDocumentById(call: ApplicationCall) {
        val response = AuthorizationDocument.Response(
            meta = ResponseMeta(),
        )
        call.respond(status = HttpStatusCode.OK, message = response)
    }
}
