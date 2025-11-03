package no.elhub.auth.features.documents.query

import arrow.core.Either
import arrow.core.raise.either
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.parties.AuthorizationParty
import no.elhub.auth.features.parties.PartyRepository

data class QueryDocumentResult(
    val document: AuthorizationDocument,
    val requestedByParty: AuthorizationParty,
    val requestedFromParty: AuthorizationParty
)

class Handler(
    private val documentRepo: DocumentRepository,
    private val partyRepo: PartyRepository,
) {
    operator fun invoke(query: Query): Either<QueryError, List<QueryDocumentResult>> = either {
        val documents = documentRepo.findAll()
            .mapLeft { QueryError.IOError }
            .bind()

        val partyIds = (documents.map { it.requestedBy } + documents.map { it.requestedFrom }).distinct()

        val parties = partyIds.mapNotNull { id ->
            partyRepo.find(id).getOrNull()?.let { id to it }
        }.toMap()

        documents.map { doc ->
            val requestedByParty = parties[doc.requestedBy]
                ?: raise(QueryError.IOError)
            val requestedFromParty = parties[doc.requestedFrom]
                ?: raise(QueryError.IOError)

            QueryDocumentResult(doc, requestedByParty, requestedFromParty)
        }
    }
}
