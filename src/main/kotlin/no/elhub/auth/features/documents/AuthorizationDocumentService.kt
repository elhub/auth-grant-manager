package no.elhub.auth.features.documents

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import no.elhub.auth.features.documents.jsonApiSpec.PostAuthorizationDocument
import no.elhub.auth.model.AuthorizationDocument
import no.elhub.auth.model.ResponseMeta
import no.elhub.auth.utils.PdfGenerator
import org.koin.core.annotation.Single
import java.util.*

@Single
class AuthorizationDocumentService {

    suspend fun getDocuments(call: ApplicationCall) {
        call.respond(status = HttpStatusCode.OK, message = ResponseMeta())
    }

    fun postDocument(authorizationDocumentRequest: PostAuthorizationDocument.Request): AuthorizationDocument {
        val pdfBytes = PdfGenerator.createChangeOfSupplierConfirmationPdf(
            authorizationDocumentRequest.data.attributes.requestedTo,
            authorizationDocumentRequest.data.attributes.requestedBy
        )
        val authorizationDocument = AuthorizationDocument.of(authorizationDocumentRequest, pdfBytes)
        AuthorizationDocumentRepository.insertDocument(authorizationDocument)
        return authorizationDocument
    }

    suspend fun getDocumentById(call: ApplicationCall) {
        call.respond(status = HttpStatusCode.OK, message = ResponseMeta())
    }

    fun getDocumentFile(documentId: UUID): ByteArray? = AuthorizationDocumentRepository.getDocumentFile(documentId)

    suspend fun patchDocumentById(call: ApplicationCall) {
        call.respond(status = HttpStatusCode.OK, message = ResponseMeta())
    }
}
