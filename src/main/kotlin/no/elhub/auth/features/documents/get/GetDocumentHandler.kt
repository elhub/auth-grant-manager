package no.elhub.auth.features.documents.get

import no.elhub.auth.features.documents.common.DocumentRepository

class GetDocumentHandler(
    private val repo: DocumentRepository
) {
    suspend operator fun invoke(query: GetDocumentQuery): ByteArray? = repo[query.id]
}
