package no.elhub.auth.domain.document

import java.util.UUID

interface DocumentRepository {
    fun insertDocument(doc: AuthorizationDocument)
    fun getDocumentFile(id: UUID): ByteArray?
}
