package no.elhub.auth.features.documents.query

import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository

class QueryDocumentsHandler(
    private val repo: DocumentRepository
) {
    operator fun invoke(query: QueryDocumentsQuery): List<AuthorizationDocument> = repo.findAll()
}
