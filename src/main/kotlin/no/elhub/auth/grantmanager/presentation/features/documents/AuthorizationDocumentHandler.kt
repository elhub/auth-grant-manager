package no.elhub.auth.grantmanager.presentation.features.documents

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import java.util.UUID
import no.elhub.auth.grantmanager.data.models.AuthorizationDocumentDbEntity
import no.elhub.auth.grantmanager.presentation.model.ResponseMeta

class AuthorizationDocumentHandler {

    suspend fun getDocuments(call: ApplicationCall) {
        call.respond(status = HttpStatusCode.OK, message = ResponseMeta())
    }

    suspend fun postDocument(
            authorizationDocumentRequest: PostAuthorizationDocumentRequest
    ): AuthorizationDocumentDbEntity {}

    suspend fun getDocumentById(call: ApplicationCall) {
        call.respond(status = HttpStatusCode.OK, message = ResponseMeta())
    }

    fun getDocumentFile(documentId: UUID): ByteArray? =
            AuthorizationDocumentRepository.getDocumentFile(documentId)

    suspend fun patchDocumentById(call: ApplicationCall) {
        call.respond(status = HttpStatusCode.OK, message = ResponseMeta())
    }
}
